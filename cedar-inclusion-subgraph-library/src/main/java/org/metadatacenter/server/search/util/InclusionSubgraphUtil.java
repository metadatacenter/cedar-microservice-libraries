package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.JsonSchemaConstants;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.InclusionSubgraphNodeOperation;
import org.metadatacenter.model.request.inclusionsubgraph.*;
import org.metadatacenter.proxy.ArtifactProxy;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.util.ModelUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.metadatacenter.model.ModelNodeNames.*;

public class InclusionSubgraphUtil {

  private static final Logger log = LoggerFactory.getLogger(InclusionSubgraphUtil.class);

  private InclusionSubgraphUtil() {
  }

  public static void updateResourceInclusionInfo(CedarRequestContext context, CedarConfig cedarConfig,
                                                 FolderServerResourceExtract resource,
                                                 InclusionSubgraphServiceSession inclusionSubgraphSession) {
    Response responseFromArtifact = null;
    try {
      responseFromArtifact =
          ArtifactProxy.executeResourceGetByProxyFromArtifactServer(cedarConfig.getMicroserviceUrlUtil(), null,
              resource.getType(), resource.getId(), Optional.empty(),
              context);
      InputStream is = (InputStream) responseFromArtifact.getEntity();
      JsonNode entityJsonNode = JsonMapper.MAPPER.readTree(is);
      updateResourceInclusionInfo(resource, inclusionSubgraphSession, entityJsonNode);
    } catch (CedarProcessingException e) {
      log.error("Error while retrieving artifact from artifact server", e);
    } catch (RuntimeException | IOException e) {
      log.error("Error while processing artifact response from artifact server", e);
    }
  }

  public static void updateResourceInclusionInfo(FolderServerResourceExtract resource,
                                                 InclusionSubgraphServiceSession inclusionSubgraphSession,
                                                 JsonNode entityJsonNode) {
    List<String> includedIds = extractFirstLevelIncludedIds(entityJsonNode);
    inclusionSubgraphSession.updateInclusionArcs(resource.getResourceId(), includedIds);
  }

  private static List<String> extractFirstLevelIncludedIds(JsonNode artifact) {
    List<String> linkIds = new ArrayList<>();
    if (artifact == null) {
      return linkIds;
    }
    JsonNode properties = artifact.get(JsonSchemaConstants.PROPERTIES);
    if (properties == null || !properties.isObject()) {
      return linkIds;
    }
    for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
      String fieldName = it.next();
      JsonNode embedded = null;
      if (!ModelUtil.isSpecialField(fieldName)) {
        JsonNode candidate = properties.get(fieldName);
        if (candidate == null || !candidate.isObject()) {
          continue;
        }
        JsonNode typeNode = candidate.get(JsonSchemaConstants.TYPE);
        if (typeNode == null || !typeNode.isTextual()) {
          continue;
        }
        String typeValue = typeNode.textValue();
        if (JsonSchemaConstants.TYPE_VALUE_OBJECT.equals(typeValue)) {
          // single embedded artifact
          embedded = candidate;
        } else if (JsonSchemaConstants.TYPE_VALUE_ARRAY.equals(typeValue)) {
          // multi embedded artifact
          embedded = candidate.get(JsonSchemaConstants.ITEMS);
        }
      }
      if (embedded != null && embedded.isObject()) {
        JsonNode atTypeNode = embedded.get(LinkedData.TYPE);
        JsonNode atIdNode = embedded.get(LinkedData.ID);
        if (atTypeNode == null || !atTypeNode.isTextual() || atIdNode == null || !atIdNode.isTextual()
            || atIdNode.textValue().isBlank()) {
          continue;
        }
        String atType = atTypeNode.textValue();
        String atId = atIdNode.textValue();
        if (CedarConstants.TEMPLATE_FIELD_TYPE_URI.equals(atType)) {
          linkIds.add(atId);
        } else if (CedarConstants.TEMPLATE_ELEMENT_TYPE_URI.equals(atType)) {
          linkIds.add(atId);
        }
      }
    }
    return linkIds;
  }

  public static InclusionSubgraphResponse buildAffectedTree(InclusionSubgraphRequest treeRequest,
                                                            InclusionSubgraphServiceSession inclusionSubgraphSession) {
    String rootId = treeRequest.getId();
    InclusionSubgraphResponse response = new InclusionSubgraphResponse();
    response.setId(rootId);
    response.setElements(computeAffectedElements(rootId, treeRequest.getElements(), inclusionSubgraphSession));
    response.setTemplates(computeAffectedTemplates(rootId, treeRequest.getTemplates(), inclusionSubgraphSession));

    return response;
  }

  private static Map<String, InclusionSubgraphTemplate> computeAffectedTemplates(String id, Map<String,
                                                                                     InclusionSubgraphTemplate> requestTemplates,
                                                                                 InclusionSubgraphServiceSession inclusionSubgraphSession) {
    Map<String, InclusionSubgraphTemplate> templates = new HashMap<>();
    CedarUntypedSchemaArtifactId aid = CedarUntypedSchemaArtifactId.build(id);
    List<FolderServerTemplate> includingTemplates = inclusionSubgraphSession.listIncludingTemplates(aid);
    for (FolderServerTemplate template : includingTemplates) {
      InclusionSubgraphTemplate t = InclusionSubgraphTemplate.fromFolderServerTemplate(template);
      String templateId = t.getId();
      templates.put(templateId, t);
      if (requestTemplates != null && requestTemplates.containsKey(templateId)) {
        InclusionSubgraphTemplate inclusionSubgraphTemplate = requestTemplates.get(templateId);
        if (inclusionSubgraphTemplate != null) {
          t.setOperation(inclusionSubgraphTemplate.getOperation());
        }
      }
    }
    return templates;
  }

  private static Map<String, InclusionSubgraphElement> computeAffectedElements(String id, Map<String,
                                                                                   InclusionSubgraphElement> requestElements,
                                                                               InclusionSubgraphServiceSession inclusionSubgraphSession) {
    Map<String, InclusionSubgraphElement> elements = new HashMap<>();
    CedarUntypedSchemaArtifactId aid = CedarUntypedSchemaArtifactId.build(id);
    List<FolderServerElement> includingElements = inclusionSubgraphSession.listIncludingElements(aid);
    for (FolderServerElement element : includingElements) {
      InclusionSubgraphElement e = InclusionSubgraphElement.fromFolderServerElement(element);
      String elementId = e.getId();
      elements.put(elementId, e);
      if (requestElements != null && requestElements.containsKey(elementId)) {
        InclusionSubgraphElement inclusionSubgraphElement = requestElements.get(elementId);
        if (inclusionSubgraphElement != null) {
          e.setOperation(inclusionSubgraphElement.getOperation());
        }
        if (inclusionSubgraphElement != null &&
            inclusionSubgraphElement.getOperation() == InclusionSubgraphNodeOperation.UPDATE) {
          e.setElements(computeAffectedElements(elementId, inclusionSubgraphElement.getElements(),
              inclusionSubgraphSession));
          e.setTemplates(computeAffectedTemplates(elementId, inclusionSubgraphElement.getTemplates(),
              inclusionSubgraphSession));

        }
      }
    }
    return elements;
  }

  public static InclusionSubgraphTodoList updateResources(InclusionSubgraphResponse treeResponse) {
    InclusionSubgraphTodoList todoList = new InclusionSubgraphTodoList();
    recursivelyUpdateElements(treeResponse.getId(), treeResponse.getElements(), todoList);
    updateTemplates(treeResponse.getId(), treeResponse.getTemplates(), todoList);
    return todoList;
  }

  private static void recursivelyUpdateElements(String sourceId, Map<String, InclusionSubgraphElement> elements,
                                                InclusionSubgraphTodoList todoList) {
    if (elements != null) {
      for (InclusionSubgraphElement element : elements.values()) {
        updateElement(sourceId, element, todoList);
        recursivelyUpdateElements(element.getId(), element.getElements(), todoList);
        updateTemplates(element.getId(), element.getTemplates(), todoList);
      }
    }
  }

  private static void updateTemplates(String sourceId, Map<String, InclusionSubgraphTemplate> templates,
                                      InclusionSubgraphTodoList todoList) {
    if (templates != null) {
      for (InclusionSubgraphTemplate template : templates.values()) {
        updateTemplate(sourceId, template, todoList);
      }
    }
  }

  private static void updateElement(String sourceId, InclusionSubgraphElement element,
                                    InclusionSubgraphTodoList todoList) {
    if (element.getOperation() == InclusionSubgraphNodeOperation.UPDATE) {
      InclusionSubgraphTodoElement todo = new InclusionSubgraphTodoElement();
      todo.setSourceId(sourceId);
      todo.setTargetId(element.getId());
      todoList.addTodoElement(todo);
    }
  }

  private static void updateTemplate(String sourceId, InclusionSubgraphTemplate template,
                                     InclusionSubgraphTodoList todoList) {
    if (template.getOperation() == InclusionSubgraphNodeOperation.UPDATE) {
      InclusionSubgraphTodoElement todo = new InclusionSubgraphTodoElement();
      todo.setSourceId(sourceId);
      todo.setTargetId(template.getId());
      todoList.addTodoElement(todo);
    }
  }


  public static boolean updateSubdocumentByAtId(JsonNode parentDocument, String idToBeReplaced, JsonNode newDocument) {
    return findAndReplaceDocumentNode(null, null, parentDocument, idToBeReplaced, newDocument,
        (ObjectNode) parentDocument);
  }

  private static boolean findAndReplaceDocumentNode(String key, ObjectNode parent, JsonNode currentNode,
                                                    String idToBeReplaced, JsonNode newDocument, ObjectNode root) {
    if (currentNode.has(LinkedData.ID) && currentNode.get(LinkedData.ID).asText().equals(idToBeReplaced)) {
      if (parent != null) {
        Boolean requiredValue = getCurrentRequiredValue((ObjectNode)currentNode);
        setRequiredValue((ObjectNode)newDocument, requiredValue);
        parent.replace(key, newDocument);
        updateUiMetadata(root, newDocument, key);
      }
      return true;
    }

    Iterator<String> fieldNames = currentNode.fieldNames();
    while (fieldNames.hasNext()) {
      String childKey = fieldNames.next();
      JsonNode child = currentNode.get(childKey);
      if (child.isObject()) {
        boolean found = findAndReplaceDocumentNode(childKey, (ObjectNode) currentNode, child, idToBeReplaced,
            newDocument, root);
        if (found) {
          return true;
        }
      }
    }
    return false;
  }

  private static void setRequiredValue(ObjectNode root, Boolean requiredValue) {
    if (requiredValue == null) {
      return;
    }
    if (!root.has(VALUE_CONSTRAINTS) || !root.get(VALUE_CONSTRAINTS).isObject()) {
      root.putObject(VALUE_CONSTRAINTS);
    }
    ObjectNode valueConstraints = (ObjectNode) root.get(VALUE_CONSTRAINTS);
    valueConstraints.put(VALUE_CONSTRAINTS_REQUIRED_VALUE, requiredValue);
  }

  private static Boolean getCurrentRequiredValue(ObjectNode root) {
    if (!root.has(VALUE_CONSTRAINTS) || !root.get(VALUE_CONSTRAINTS).isObject()) {
      return null;
    }
    ObjectNode valueConstraints = (ObjectNode) root.get(VALUE_CONSTRAINTS);
    if (!valueConstraints.has(VALUE_CONSTRAINTS_REQUIRED_VALUE)) {
      return null;
    }
    return valueConstraints.get(VALUE_CONSTRAINTS_REQUIRED_VALUE).asBoolean();
  }

  private static void updateUiMetadata(ObjectNode root, JsonNode newDocument, String nameRoBeReplaced) {
    if (!newDocument.has(SCHEMA_ORG_NAME) || !newDocument.has(SCHEMA_ORG_DESCRIPTION)) {
      return;
    }
    if (!root.has(UI) || !root.get(UI).isObject()) {
      return;
    }

    String fieldName = newDocument.get(SCHEMA_ORG_NAME).asText();
    String description = newDocument.get(SCHEMA_ORG_DESCRIPTION).asText();

    ObjectNode uiNode = (ObjectNode) root.get(UI);

    ObjectNode labels = uiNode.has(UI_PROPERTY_LABELS) && uiNode.get(UI_PROPERTY_LABELS).isObject()
        ? (ObjectNode) uiNode.get(UI_PROPERTY_LABELS)
        : uiNode.putObject(UI_PROPERTY_LABELS);
    labels.put(nameRoBeReplaced, fieldName);

    ObjectNode descriptions = uiNode.has(UI_PROPERTY_DESCRIPTIONS) && uiNode.get(UI_PROPERTY_DESCRIPTIONS).isObject()
        ? (ObjectNode) uiNode.get(UI_PROPERTY_DESCRIPTIONS)
        : uiNode.putObject(UI_PROPERTY_DESCRIPTIONS);
    descriptions.put(nameRoBeReplaced, description);
  }

}
