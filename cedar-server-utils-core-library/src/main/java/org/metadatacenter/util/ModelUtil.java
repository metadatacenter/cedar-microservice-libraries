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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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

  /**
   * Gives every direct child an identifier and provenance, for an artifact being created. Every child is
   * new, so each receives a full creation record.
   */
  public static List<MintedChildId> ensureFieldIdsRecursively(JsonNode genericInstance, ProvenanceInfo pi,
                                                              ProvenanceUtil provenanceUtil,
                                                              LinkedDataUtil linkedDataUtil) {
    return ensureFieldIdsRecursively(genericInstance, null, pi, provenanceUtil, linkedDataUtil);
  }

  /**
   * Gives every direct child an identifier, and provenance describing what this write actually did to it.
   * <p>
   * Three cases, distinguished by comparing each child against the same property in the stored artifact.
   * A child with no counterpart there is new and gets a full creation record. A child whose content
   * differs keeps the creation record it already had and takes a fresh last-modified stamp. A child whose
   * content is identical keeps all four values unchanged, so its dates continue to describe the last write
   * that touched it rather than the last write that touched its parent.
   * <p>
   * This replaces stamping a creation record onto every child of every save, which reassigned the
   * authorship of long-standing children to whoever saved the parent, and left every child's
   * last-modified date reporting the parent's save. Editing a template usually does edit children, so the
   * old behaviour was often approximately right about modification and never right about creation.
   * <p>
   * The comparison ignores the four provenance keys. Comparing them would make every child differ on the
   * first write after this ships, and their values are the thing being decided.
   *
   * @param storedArtifact the artifact as currently stored, or null when this write creates it
   */
  public static List<MintedChildId> ensureFieldIdsRecursively(JsonNode genericInstance, JsonNode storedArtifact,
                                                              ProvenanceInfo pi, ProvenanceUtil provenanceUtil,
                                                              LinkedDataUtil linkedDataUtil) {
    Map<String, JsonNode> storedChildren = childrenByName(storedArtifact);
    List<MintedChildId> minted = new ArrayList<>();
    // A malformed multi-instance child carrying no 'items' object is skipped rather than dereferenced:
    // enforceChildArtifactTypes rejects the artifact with a 400 that names the property.
    forEachChild(genericInstance, (name, child) -> {
      if (!hasUsableChildId(child)) {
        JsonNode replaced = child.get("@id");
        String newId = generateNewChildId(child, linkedDataUtil);
        ((ObjectNode) child).put("@id", newId);
        minted.add(new MintedChildId(name,
            replaced == null || replaced.isNull() ? null : replaced.asText(), newId));
      }
      JsonNode stored = storedChildren.get(name);
      if (stored == null) {
        provenanceUtil.addProvenanceInfo(child, pi);
      } else if (childContentDiffers(child, stored)) {
        provenanceUtil.preserveCreationProvenance(child, stored);
        provenanceUtil.patchProvenanceInfo(child, pi);
      } else {
        provenanceUtil.copyProvenance(child, stored);
      }
    });
    return minted;
  }

  /**
   * An identifier this write assigned to a child, and whatever the request held there before.
   * <p>
   * The replaced value is the point. Minting over a value that is not an absolute IRI destroys it, and
   * nothing else in the stack records what it was, so a repair pass cannot afterwards say which artifacts
   * it altered or what they held. A null {@code replaced} means the child arrived without an identifier
   * at all, where there is nothing to lose.
   */
  public record MintedChildId(String property, String replaced, String minted) {
    public boolean destroyedAValue() {
      return replaced != null && !replaced.isBlank();
    }
  }

  /** The direct children of an artifact, by the property that holds each one. */
  public static Map<String, JsonNode> childrenByName(JsonNode genericInstance) {
    Map<String, JsonNode> children = new HashMap<>();
    forEachChild(genericInstance, children::put);
    return children;
  }

  /**
   * Whether two versions of the same child differ in anything but their provenance.
   */
  private static boolean childContentDiffers(JsonNode child, JsonNode stored) {
    return !withoutProvenance(child).equals(withoutProvenance(stored));
  }

  /**
   * A copy of a child with provenance removed at every level, not only its own.
   * <p>
   * Below the direct children nothing normalises provenance: those values are whatever the request
   * supplied. Leaving them in the comparison let a request that differed only in a nested field's
   * timestamp count as a change to the child containing it, which then took a modification stamp saying
   * it had been edited. The comparison asks whether this write changed the child's content, and
   * provenance is not content at any depth.
   */
  private static JsonNode withoutProvenance(JsonNode child) {
    JsonNode copy = child.deepCopy();
    removeProvenanceRecursively(copy);
    return copy;
  }

  private static void removeProvenanceRecursively(JsonNode node) {
    if (node.isObject()) {
      ((ObjectNode) node).remove(ProvenanceUtil.PROVENANCE_KEYS);
      node.forEach(ModelUtil::removeProvenanceRecursively);
    } else if (node.isArray()) {
      node.forEach(ModelUtil::removeProvenanceRecursively);
    }
  }

  /**
   * Whether a child's '@id' is an identifier at all: present, a string, and an absolute IRI.
   * <p>
   * One rule replaces the former test for the 'tmp-' prefix the Template Designer mints, because a
   * temporary identifier is not absolute and so fails this on its own. Nothing about the prefix is
   * special any more, which also catches what the prefix test let through: an empty string, a relative
   * reference, and a value whose case or whitespace differs from what the frontend happens to write.
   * <p>
   * An absolute IRI under a base this repository does not own is still an identifier and is kept.
   * Imported artifacts carry them, and minting over one would break whatever refers to it.
   */
  public static boolean hasUsableChildId(JsonNode fieldCandidate) {
    JsonNode idNode = fieldCandidate.get("@id");
    if (idNode == null || !idNode.isTextual()) {
      return false;
    }
    return isAbsoluteIri(idNode.textValue());
  }

  private static boolean isAbsoluteIri(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      return new URI(value.trim()).isAbsolute();
    } catch (URISyntaxException e) {
      return false;
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

  /**
   * The names of any direct children whose identifier prefix contradicts their '@type' -- an element
   * holding an identifier under 'template-fields', or a field holding one under 'template-elements'.
   * <p>
   * Writes now mint the correct prefix, but artifacts damaged before that keep what they have: the wrong
   * IRI is well formed, so validation accepts it, and it is absolute, so it is never minted again. This
   * reports them so a repair pass can find them; nothing here refuses a write over one, because the
   * identifier is what other artifacts already refer to and choosing a replacement is not a decision the
   * write path should make.
   * <p>
   * A child whose identifier carries neither prefix -- one under a foreign base, say -- is not reported,
   * because there is nothing to contradict.
   */
  public static List<String> childrenWithMismatchedIdPrefix(JsonNode genericInstance) {
    List<String> mismatched = new ArrayList<>();
    forEachChild(genericInstance, (name, child) -> {
      JsonNode idNode = child.get("@id");
      if (idNode == null || !idNode.isTextual() || !hasRecognisedChildType(child)) {
        return;
      }
      String id = idNode.textValue();
      String expected = "/" + prefixOf(childResourceType(child)) + "/";
      String other = "/" + prefixOf(childResourceType(child) == CedarResourceType.ELEMENT
          ? CedarResourceType.FIELD : CedarResourceType.ELEMENT) + "/";
      if (!id.contains(expected) && id.contains(other)) {
        mismatched.add(name);
      }
    });
    return mismatched;
  }

  private static String prefixOf(CedarResourceType resourceType) {
    return resourceType.getPrefix();
  }

  /**
   * The direct children of a template or element: the entries of 'properties' that are field or element
   * schemas rather than the reserved keys, unwrapped from the array that carries a multi-instance one.
   * Both the identifier logic and the checks over it walk the same shape, so they walk it once here.
   */
  public static void forEachChild(JsonNode genericInstance, BiConsumer<String, JsonNode> visitor) {
    JsonNode properties = genericInstance == null ? null : genericInstance.get("properties");
    if (properties == null || !properties.isObject()) {
      return;
    }
    Iterator<Map.Entry<String, JsonNode>> it = properties.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> entry = it.next();
      JsonNode candidate = entry.getValue();
      if (!candidate.isObject() || candidate.get("type") == null || isSpecialField(entry.getKey())) {
        continue;
      }
      String type = candidate.get("type").asText();
      JsonNode child = candidate;
      if ("array".equals(type)) {
        child = candidate.get("items");
      } else if (!"object".equals(type)) {
        continue;
      }
      if (child != null && child.isObject()) {
        visitor.accept(entry.getKey(), child);
      }
    }
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


