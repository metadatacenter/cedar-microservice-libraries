package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.InfoField;
import org.metadatacenter.server.search.extraction.model.FieldValue;
import org.metadatacenter.server.search.extraction.model.TemplateNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.model.ModelNodeNames.JSON_LD_ID;
import static org.metadatacenter.model.ModelNodeNames.JSON_LD_VALUE;
import static org.metadatacenter.model.ModelNodeNames.RDFS_LABEL;
import static org.metadatacenter.model.ModelNodeNames.SCHEMA_IS_BASED_ON;

/**
 * Utilities to extract information from CEDAR Template Instances
 */
public class TemplateInstanceContentExtractor {

  private static final Logger log = LoggerFactory.getLogger(TemplateInstanceContentExtractor.class);

  private ExtractionUtils extractionUtils;
  private TemplateContentExtractor templateContentExtractor;
  /**
   * Cache of template nodes, which is used to avoid retrieving and parsing the same template multiple times.
   */
  private HashMap<String, HashMap<String, TemplateNode>> templateNodesCache;

  public TemplateInstanceContentExtractor(CedarConfig cedarConfig) {
    this.extractionUtils = new ExtractionUtils(cedarConfig);
    this.templateContentExtractor = new TemplateContentExtractor();
    this.templateNodesCache = new HashMap<>();
  }

  /**
   * Generates a list of InfoField objects with information for fields and (when appropriate) their values.
   * @param folderServerNode
   * @param requestContext
   * @param isIndexRegenerationTask
   * @return
   * @throws CedarProcessingException
   */
  public List<InfoField> generateInfoFields(FileSystemResource folderServerNode,
                                            CedarRequestContext requestContext, boolean isIndexRegenerationTask)
      throws CedarProcessingException {

    if (folderServerNode.getType().equals(CedarResourceType.INSTANCE)) {
      return generateInfoFieldsFromInstance(folderServerNode, requestContext, isIndexRegenerationTask);
    }
    else if (folderServerNode.getType().equals(CedarResourceType.TEMPLATE) ||
        folderServerNode.getType().equals(CedarResourceType.ELEMENT) ||
        folderServerNode.getType().equals(CedarResourceType.FIELD)) {
      return generateInfoFieldsFromSchema(folderServerNode, requestContext);
    }
    else {
      throw new CedarProcessingException("The artifact must be an Instance, a Template, an Element, or a Field, but it is a "
          + folderServerNode.getType().name());
    }
  }

  /**
   * Generates a list of PossibleValues objects from a template field.
   *
   * @param folderServerNode
   * @param requestContext
   * @return
   */
  public List<String> generateValueSetsURIs(FileSystemResource folderServerNode, CedarRequestContext requestContext)
    throws CedarProcessingException {

    if (folderServerNode.getType().equals(CedarResourceType.FIELD)) {

      List<String> valueSetsURIs = new ArrayList<>();
      // Retrieve the template field and parse and return URIs of value sets if present
      JsonNode schema = extractionUtils.getArtifactById(folderServerNode.getId(), folderServerNode.getType(), requestContext);
      List<TemplateNode> schemaNodes = templateContentExtractor.getTemplateNodes(schema, folderServerNode.getType());

      for (TemplateNode node : schemaNodes) {
        if (node.getType().equals(CedarResourceType.FIELD)) {
          List<String> valueSetURIs = node.getValueSetURIs();
          valueSetsURIs.addAll(valueSetURIs);
        }
      }
      return valueSetsURIs;
    } else {
      throw new CedarProcessingException(
        "The artifact must be a Template Field, but it is a " + folderServerNode.getType().name());
    }
  }

  /**
   * Generates a list of InfoField objects from a template instance. These InfoFields objects contain information
   * about the template fields and the values entered for the template instance
   *
   * @param folderServerNode
   * @param requestContext
   * @return
   * @throws CedarProcessingException
   */
  private List<InfoField> generateInfoFieldsFromInstance(FileSystemResource folderServerNode,
                                            CedarRequestContext requestContext, boolean isIndexRegenerationTask)
      throws CedarProcessingException {

    if (folderServerNode.getType().equals(CedarResourceType.INSTANCE)) {

      List<InfoField> infoFields = new ArrayList<>();
      JsonNode templateInstance = extractionUtils.getArtifactById(folderServerNode.getId(),
          folderServerNode.getType(), requestContext);
      String templateId = templateInstance.get(SCHEMA_IS_BASED_ON).asText();

      HashMap<String, TemplateNode> nodesMap = null;
      // If it's an index regeneration task the cache will be needed to avoid retrieving and parsing the same
      // template multiple times (once per template instance). If the cache contains the template nodes, return them
      if (isIndexRegenerationTask && templateNodesCache.containsKey(templateId)) {
        nodesMap = templateNodesCache.get(templateId);
      }
      // Otherwise, retrieve the template and parse it
      else {
        JsonNode template = extractionUtils.getArtifactById(templateId, CedarResourceType.TEMPLATE, requestContext);
        List<TemplateNode> templateNodes = templateContentExtractor.getTemplateNodes(template, CedarResourceType.TEMPLATE);
        nodesMap = new HashMap<>();
        for (TemplateNode node : templateNodes) {
          nodesMap.put(node.generatePathDotNotation(), node);
        }
        if (isIndexRegenerationTask) {
          templateNodesCache.put(templateId, nodesMap);
        }
      }

      List<FieldValue> fieldValues = getFieldValues(templateInstance, nodesMap, null, null);

      for (FieldValue fieldValue : fieldValues) {
        String fieldName = null;
        String fieldPrefLabel = null;
        if (nodesMap.containsKey(fieldValue.generatePathDotNotation())) {
          TemplateNode templateNode = nodesMap.get(fieldValue.generatePathDotNotation());
          fieldName = templateNode.getName();
          fieldPrefLabel = templateNode.getPrefLabel();
        } else {
          throw new CedarProcessingException("Field path not found in nodesMap: " +
              fieldValue.generatePathDotNotation());
        }
        // Add to the list if it's not already there
        InfoField infoField = null;
        try {
          String fieldValueUri = fieldValue.getFieldValueUri();
          if (fieldValueUri != null) {
            fieldValueUri = URLEncoder.encode(fieldValueUri, StandardCharsets.UTF_8.toString());
          }
          infoField = new InfoField(fieldName, fieldPrefLabel, fieldValue.generatePathBracketNotation(),
              fieldValue.getFieldValue(), fieldValueUri);
        } catch (UnsupportedEncodingException e) {
          throw new CedarProcessingException("Encoding error", e);
        }
        if (!infoFields.contains(infoField)) {
          infoFields.add(infoField);
        }
      }
      return infoFields;
    } else {
      throw new CedarProcessingException("The artifact must be an Instance but it is a "
          + folderServerNode.getType().name());
    }
  }

  /**
   * Generates a list of InfoField objects from a template or an element.
   *
   * @param folderServerNode
   * @param requestContext
   * @return
   */
  private List<InfoField> generateInfoFieldsFromSchema(FileSystemResource folderServerNode,
                                                       CedarRequestContext requestContext) throws CedarProcessingException {

    if (folderServerNode.getType().equals(CedarResourceType.TEMPLATE) ||
        folderServerNode.getType().equals(CedarResourceType.ELEMENT) ||
        folderServerNode.getType().equals(CedarResourceType.FIELD)) {

      List<InfoField> infoFields = new ArrayList<>();
      // Retrieve the template/element/field and parse it to extract its nodes
      JsonNode schema = extractionUtils.getArtifactById(folderServerNode.getId(), folderServerNode.getType(), requestContext);
      List<TemplateNode> schemaNodes = templateContentExtractor.getTemplateNodes(schema, folderServerNode.getType());

      for (TemplateNode node : schemaNodes) {
        if (node.getType().equals(CedarResourceType.FIELD)) {
          List<String> fieldValues = node.getValueSetURIs();
          infoFields.add(new InfoField(node.getName(), node.getPrefLabel(), node.generatePathBracketNotation(), null, null));
        }
      }
      return infoFields;

    } else {
      throw new CedarProcessingException("The artifact must be a Template, an Element, or a Field, but it is a "
          + folderServerNode.getType().name());
    }
  }

  /**
   * Extracts field and field values from a template instance. Note that some information, such as the field name,
   * cannot be extracted from the instance because it's not available there. The field names will be extracted from
   * the template in the method 'generateInfoFields'.
   *
   * @param currentNode
   * @param templateNodesMap
   * @param currentPath Used internally
   * @param results Used internally
   * @return
   * @throws CedarProcessingException
   */
  private List<FieldValue> getFieldValues(JsonNode currentNode, HashMap<String, TemplateNode> templateNodesMap,
                                          List<String> currentPath, List<FieldValue> results) throws CedarProcessingException {

    if (currentPath == null) {
      currentPath = new ArrayList<>();
    }
    if (results == null) {
      results = new ArrayList();
    }

    Iterator<Map.Entry<String, JsonNode>> jsonNodesIterator = currentNode.fields();
    while (jsonNodesIterator.hasNext()) {
      Map.Entry<String, JsonNode> currentNodeMap = jsonNodesIterator.next();
      List<String> tmpPath = new ArrayList<>(currentPath);
      tmpPath.add(currentNodeMap.getKey());
      String tmpPathDotNotation = getPathDotNotation(tmpPath);
      TemplateNode templateNode = null;
      if (templateNodesMap.containsKey(tmpPathDotNotation)) {
        templateNode = templateNodesMap.get(tmpPathDotNotation);
        // Not an array
        if (!templateNode.isArray()) {
          // Template Element
          if (templateNode.isTemplateElementNode()) {
            getFieldValues(currentNodeMap.getValue(), templateNodesMap, tmpPath, results);
          }
          // Template Field
          else if (templateNode.isTemplateFieldNode()) {
            // Extract value and save it to the results
            results.add(generateFieldValue(currentNodeMap.getValue(), tmpPath));
          } else {
            throw new CedarProcessingException("Unrecognized node type. The template node must be either a " +
                "Template Field or a Template Element. Node type: " + templateNode.getName());
          }
        }
        // Array
        else {
          // Array of template elements
          if (templateNode.isTemplateElementNode()) {
            for (JsonNode node : currentNodeMap.getValue()) {
              getFieldValues(node, templateNodesMap, tmpPath, results);
            }
          }
          // Array of template fields
          else if (templateNode.isTemplateFieldNode()) {
            for (JsonNode node : currentNodeMap.getValue()) {
              // Extract value and save it to the results
              results.add(generateFieldValue(node, tmpPath));
            }
          } else {
            throw new CedarProcessingException("Unrecognized node type. The template node must be either a " +
                "Template Field or a Template Element. Node type: " + templateNode.getName());
          }
        }
      } else {
        // Node not found in the map of template nodes. It is not a relevant node (e.g. @context) so we ignore it.
      }
    }
    return results;
  }

  private String getPathDotNotation(List<String> path) {
    return String.join(".", path);
  }

  private FieldValue generateFieldValue(JsonNode fieldNode, List<String> fieldPath) {
    FieldValue fieldValue = new FieldValue();
    fieldValue.setFieldKey(fieldPath.get(fieldPath.size() - 1));
    fieldValue.setFieldPath(fieldPath);

    // Regular value
    if (fieldNode.hasNonNull(JSON_LD_VALUE) && !fieldNode.get(JSON_LD_VALUE).asText().isEmpty()) {
      fieldValue.setFieldValue(fieldNode.get(JSON_LD_VALUE).asText());
    }
    // Ontology term
    else {
      if (fieldNode.hasNonNull(RDFS_LABEL) && !fieldNode.get(RDFS_LABEL).asText().isEmpty()) {
        fieldValue.setFieldValue(fieldNode.get(RDFS_LABEL).asText());
      }
      if (fieldNode.hasNonNull(JSON_LD_ID) && !fieldNode.get(JSON_LD_ID).asText().isEmpty()) {
        fieldValue.setFieldValueUri(fieldNode.get(JSON_LD_ID).asText());
      }
    }
    return fieldValue;
  }

  public void clearNodesCache() {
    this.templateNodesCache.clear();
    log.info("The template nodes cache has been cleared");
  }

}
