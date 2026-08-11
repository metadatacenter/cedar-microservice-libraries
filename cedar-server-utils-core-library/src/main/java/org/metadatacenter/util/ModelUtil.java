package org.metadatacenter.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.metadatacenter.model.ModelPaths.*;

public class ModelUtil {

  private static final String specialFieldPattern = "(^@)|(^_)|(^schema:)|(^pav:)|(^oslc:)";

  private ModelUtil() {
  }

  public static boolean isSpecialField(String fieldName) {
    // Create a Pattern object
    Pattern r = Pattern.compile(specialFieldPattern);
    // Now create matcher object.
    Matcher m = r.matcher(fieldName);
    return m.find();
  }

  private static JsonPointerValuePair extractStringFromPointer(JsonNode jsonNode, String pointer) {
    JsonPointerValuePair r = new JsonPointerValuePair();
    r.setPointer(pointer);
    JsonNode titleNode = jsonNode.at(r.getPointer());
    if (titleNode != null && titleNode.isTextual()) {
      r.setValue(titleNode.textValue().trim());
    }
    return r;
  }

  public static JsonPointerValuePair extractAtIdFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, AT_ID);
  }

  public static JsonPointerValuePair extractNameFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, SCHEMA_NAME);
  }

  public static JsonPointerValuePair extractDOIFromResource(JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, ANNOTATION_DOI_ID);
  }

  public static JsonPointerValuePair extractDescriptionFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, SCHEMA_DESCRIPTION);
  }

  public static JsonPointerValuePair extractIdentifierFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, SCHEMA_IDENTIFIER);
  }

  public static JsonPointerValuePair extractVersionFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, PAV_VERSION);
  }

  public static JsonPointerValuePair extractPublicationStatusFromResource(CedarResourceType resourceType, JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, BIBO_STATUS);
  }

  public static JsonPointerValuePair extractIsBasedOnFromInstance(JsonNode jsonNode) {
    return extractStringFromPointer(jsonNode, SCHEMA_IS_BASED_ON);
  }

  public static void ensureFieldIdsRecursively(JsonNode genericInstance, ProvenanceInfo pi, ProvenanceUtil provenanceUtil,
                                               LinkedDataUtil linkedDataUtil) {
    JsonNode properties = genericInstance.get("properties");
    if (properties != null) {
      Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
      while (it.hasNext()) {
        Map.Entry<String, JsonNode> entry = it.next();
        JsonNode fieldCandidate = entry.getValue();
        // If the entry is an object
        if (fieldCandidate.isObject()
            && fieldCandidate.get("type") != null
            && !ModelUtil.isSpecialField(entry.getKey())) {
          String type = fieldCandidate.get("type").asText();
          if ("object".equals(type)) {
            generateFieldIdIfTemporaryOrMissing(fieldCandidate, pi, provenanceUtil, linkedDataUtil);
            // multiple instance
          } else if ("array".equals(type)) {
            // A malformed multi-instance child can carry no 'items'. Skip it rather than dereferencing
            // null: enforceChildArtifactTypes rejects the artifact with a 400 that names the property.
            JsonNode items = fieldCandidate.get("items");
            if (items != null && items.isObject()) {
              generateFieldIdIfTemporaryOrMissing(items, pi, provenanceUtil, linkedDataUtil);
            }
          }
        }
      }
    }
  }

  private static void generateFieldIdIfTemporaryOrMissing(JsonNode fieldCandidate, ProvenanceInfo pi, ProvenanceUtil
      provenanceUtil, LinkedDataUtil linkedDataUtil) {
    provenanceUtil.addProvenanceInfo(fieldCandidate, pi);
    JsonNode idNode = fieldCandidate.get("@id");
    if (idNode == null || !idNode.isTextual() || idNode.textValue().startsWith(CedarConstants.TEMP_ID_PREFIX)) {
      ((ObjectNode) fieldCandidate).put("@id", generateNewChildId(fieldCandidate, linkedDataUtil));
    }
  }

  /**
   * The identifier minted for a child must carry the prefix of what the child actually is. Minting every
   * child as a field gave an element an IRI under 'template-fields', which no later write repairs: the
   * wrong IRI is well formed, so validation accepts it, and it is no longer temporary, so it is never
   * minted again. enforceChildArtifactTypes rejects a child whose '@type' cannot be recognised, so an
   * unrecognised type reaches this point only when validation is disabled; treat it as a field there,
   * which is the behaviour that has always applied.
   */
  private static String generateNewChildId(JsonNode fieldCandidate, LinkedDataUtil linkedDataUtil) {
    return linkedDataUtil.buildNewLinkedDataId(childResourceType(fieldCandidate));
  }

  public static CedarResourceType childResourceType(JsonNode fieldCandidate) {
    JsonNode atType = fieldCandidate.get("@type");
    if (atType != null && atType.isTextual() && CedarResourceType.AtType.ELEMENT.equals(atType.textValue())) {
      return CedarResourceType.ELEMENT;
    }
    return CedarResourceType.FIELD;
  }

  /**
   * The three '@type' values the meta-schemas allow for a child of a template or element. A static field is
   * a field as far as identifiers go, so only the element case picks a different prefix.
   */
  public static boolean hasRecognisedChildType(JsonNode fieldCandidate) {
    JsonNode atType = fieldCandidate.get("@type");
    if (atType == null || !atType.isTextual()) {
      return false;
    }
    String value = atType.textValue();
    return CedarResourceType.AtType.ELEMENT.equals(value)
        || CedarResourceType.AtType.FIELD.equals(value)
        || CedarResourceType.AtType.STATIC_FIELD.equals(value);
  }

  public static String extractDOIFromResourceContent(String content, CedarResourceType resourceType) throws CedarProcessingException {
    String doiInRequest = null;
    try {
      JsonNode folderServerNodeRequest = JsonMapper.MAPPER.readTree(content);
      if (resourceType.supportsDOI()) {
        JsonPointerValuePair doiPair = ModelUtil.extractDOIFromResource(folderServerNodeRequest);
        doiInRequest = doiPair.getValue();
      }
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
    return doiInRequest;
  }
}


