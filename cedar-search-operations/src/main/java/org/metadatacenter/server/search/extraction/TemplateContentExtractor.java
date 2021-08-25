package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.search.extraction.model.TemplateNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.metadatacenter.model.ModelNodeNames.JSON_LD_ID;
import static org.metadatacenter.model.ModelNodeNames.JSON_LD_TYPE;
import static org.metadatacenter.model.ModelNodeNames.JSON_SCHEMA_ENUM;
import static org.metadatacenter.model.ModelNodeNames.JSON_SCHEMA_ITEMS;
import static org.metadatacenter.model.ModelNodeNames.JSON_SCHEMA_ONE_OF;
import static org.metadatacenter.model.ModelNodeNames.JSON_SCHEMA_PROPERTIES;
import static org.metadatacenter.model.ModelNodeNames.SCHEMA_ORG_NAME;
import static org.metadatacenter.model.ModelNodeNames.SKOS_PREFLABEL;
import static org.metadatacenter.model.ModelNodeNames.VALUE_CONSTRAINTS;
import static org.metadatacenter.model.ModelNodeNames.VALUE_CONSTRAINTS_URI;
import static org.metadatacenter.model.ModelNodeNames.VALUE_CONSTRAINTS_VALUE_SETS;

/**
 * Utilities to extract information from CEDAR Templates/Elements/Fields
 */
public class TemplateContentExtractor {

  private static final Logger log = LoggerFactory.getLogger(TemplateContentExtractor.class);

  /**
   *
   * @param node
   * @param resourceType
   * @return
   * @throws CedarProcessingException
   */
  public List<TemplateNode> getTemplateNodes(JsonNode node, CedarResourceType resourceType) throws CedarProcessingException {
    // If it's a field, we nest it in a JsonNode to make the getSchemaNodes method work
    if (resourceType.equals(CedarResourceType.FIELD)) {
      node = new ObjectMapper().createObjectNode().set("field", node);
    }
    return getTemplateNodes(node, null, null);
  }

  // If the resource type is not specified, it assumes that it will be a Template/Element. There is no need to nest
  // the JsonNode inside another node
  public List<TemplateNode> getTemplateNodes(JsonNode schema) throws CedarProcessingException {
    return getTemplateNodes(schema, null, null);
  }

  /**
   * Returns summary information of all template nodes in the template.
   *
   * @param node Template/Element/Field in JSON
   * @param currentPath Used internally to store the current node path
   * @param results     Used internally to store the results
   * @return A list of the template elements and fields in the template, represented using the TemplateNode class
   */
  private List<TemplateNode> getTemplateNodes(JsonNode node, List<String> currentPath, List results) throws
      CedarProcessingException {
    if (currentPath == null) {
      currentPath = new ArrayList<>();
    }
    if (results == null) {
      results = new ArrayList();
    }
    Iterator<Map.Entry<String, JsonNode>> jsonFieldsIterator = node.fields();
    while (jsonFieldsIterator.hasNext()) {
      Map.Entry<String, JsonNode> jsonField = jsonFieldsIterator.next();
      final String jsonFieldKey = jsonField.getKey();
      if (jsonField.getValue().isContainerNode()) {
        JsonNode jsonFieldNode;
        boolean isArray;
        // Single-instance node
        if (!jsonField.getValue().has(JSON_SCHEMA_ITEMS)) {
          jsonFieldNode = jsonField.getValue();
          isArray = false;
        }
        // Multi-instance node
        else {
          jsonFieldNode = jsonField.getValue().get(JSON_SCHEMA_ITEMS);
          isArray = true;
        }
        // Field or Element
        if (isTemplateFieldNode(jsonFieldNode) || isTemplateElementNode(jsonFieldNode)) {

          // Get field/element identifier
          String id = null;
          if ((jsonFieldNode.get(JSON_LD_ID) != null) && (jsonFieldNode.get(JSON_LD_ID).asText().length() > 0)) {
            id = jsonFieldNode.get(JSON_LD_ID).asText();
          } else {
            throw (new CedarProcessingException(JSON_LD_ID + " not found for template field"));
          }

          // Get name
          String name = null;
          if ((jsonFieldNode.get(SCHEMA_ORG_NAME) != null) && (jsonFieldNode.get(SCHEMA_ORG_NAME).asText().length() > 0)) {
            name = jsonFieldNode.get(SCHEMA_ORG_NAME).asText();
          } else {
            // Do nothing. This field is not required.
          }

          // Get preferred label
          String prefLabel = null;
          if ((jsonFieldNode.get(SKOS_PREFLABEL) != null) && (jsonFieldNode.get(SKOS_PREFLABEL).asText().length() > 0)) {
            prefLabel = jsonFieldNode.get(SKOS_PREFLABEL).asText();
          } else {
            // Do nothing. This field is not required.
          }

          // Add json field path to the results. I create a new list to not modify currentPath
          List<String> jsonFieldPath = new ArrayList<>(currentPath);
          jsonFieldPath.add(jsonFieldKey);

          // Field
          if (isTemplateFieldNode(jsonFieldNode)) {
            // Get instance type (@type) if it exists)
            Optional<String> instanceType = getInstanceType(jsonFieldNode);

            List<String> valueSetURIs = new ArrayList<>();
            JsonNode valueConstraintsNode = jsonFieldNode.get(VALUE_CONSTRAINTS);
            if (valueConstraintsNode != null) {
              JsonNode valueSetsArrayNode = valueConstraintsNode.get(VALUE_CONSTRAINTS_VALUE_SETS);
              if (valueSetsArrayNode != null) {
                for (JsonNode valueSetNode : valueSetsArrayNode) {
                  String valueSetURI = valueSetNode.get(VALUE_CONSTRAINTS_URI).asText();

                  if (valueSetURI == null)
                    log.warn("Null value set URI value sets array in _valueConstraints node at path "
                      + currentPath + "; node=" + valueConstraintsNode);
                  else if (valueSetURI.isEmpty())
                    log.warn("Empty value set URI value sets array in _valueConstraints node at path "
                      + currentPath + "; node=" + valueConstraintsNode);
                  else
                  valueSetURIs.add(valueSetURI);
                }
              }
            }

            results.add(new TemplateNode(id, name, prefLabel, jsonFieldPath, CedarResourceType.FIELD, isArray, valueSetURIs));
          }
          // Element
          else if (isTemplateElementNode(jsonFieldNode)) {
            results.add(new TemplateNode(id, name, prefLabel, jsonFieldPath, CedarResourceType.ELEMENT, isArray,
              Collections.emptyList()));
            getTemplateNodes(jsonFieldNode, jsonFieldPath, results);
          }
        }
        // All other nodes
        else {
          getTemplateNodes(jsonFieldNode, currentPath, results);
        }
      }
    }
    return results;
  }

  /**
   * Checks if a Json node corresponds to a CEDAR template field
   *
   * @param node
   * @return
   */
  private boolean isTemplateFieldNode(JsonNode node) {
    if (node.get(JSON_LD_TYPE) != null && node.get(JSON_LD_TYPE).asText().equals(CedarResourceType.FIELD.getAtType())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks if a Json node corresponds to a CEDAR template element
   *
   * @param node
   * @return
   */
  private boolean isTemplateElementNode(JsonNode node) {
    if (node.get(JSON_LD_TYPE) != null && node.get(JSON_LD_TYPE).asText().equals(CedarResourceType.ELEMENT.getAtType())) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns the instance type of a field node
   *
   * @param fieldNode
   * @return
   */
  private Optional<String> getInstanceType(JsonNode fieldNode) {
    if (isTemplateFieldNode(fieldNode)) {
      if (fieldNode.get(JSON_SCHEMA_PROPERTIES) != null &&
          fieldNode.get(JSON_SCHEMA_PROPERTIES).get(JSON_LD_TYPE) != null &&
          fieldNode.get(JSON_SCHEMA_PROPERTIES).get(JSON_LD_TYPE).get(JSON_SCHEMA_ONE_OF) != null &&
          fieldNode.get(JSON_SCHEMA_PROPERTIES).get(JSON_LD_TYPE).get(JSON_SCHEMA_ONE_OF).size() > 0 &&
          fieldNode.get(JSON_SCHEMA_PROPERTIES).get(JSON_LD_TYPE).get(JSON_SCHEMA_ONE_OF).get(0).get(JSON_SCHEMA_ENUM) != null &&
          fieldNode.get(JSON_SCHEMA_PROPERTIES).get(JSON_LD_TYPE).get(JSON_SCHEMA_ONE_OF).get(0).get(JSON_SCHEMA_ENUM).size() > 0) {

        return Optional.of(fieldNode.get(JSON_SCHEMA_PROPERTIES).
            get(JSON_LD_TYPE).get(JSON_SCHEMA_ONE_OF).get(0).get(JSON_SCHEMA_ENUM).get(0).asText());
      }
    }
    return Optional.empty();
  }

}
