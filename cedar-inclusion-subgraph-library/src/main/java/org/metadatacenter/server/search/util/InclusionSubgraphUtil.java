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

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class InclusionSubgraphUtil {

  private static final Logger log = LoggerFactory.getLogger(InclusionSubgraphUtil.class);

  private InclusionSubgraphUtil() {
  }

  public static void updateResourceInclusionInfo(CedarRequestContext context, CedarConfig cedarConfig, FolderServerResourceExtract resource, InclusionSubgraphServiceSession inclusionSubgraphSession) {
    Response responseFromArtifact = null;
    try {
      responseFromArtifact = ArtifactProxy.executeResourceGetByProxyFromArtifactServer(cedarConfig.getMicroserviceUrlUtil(), null, resource.getType(), resource.getId(), Optional.empty(),
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

  public static void updateResourceInclusionInfo(FolderServerResourceExtract resource, InclusionSubgraphServiceSession inclusionSubgraphSession, JsonNode entityJsonNode) {
    List<String> includedIds = extractFirstLevelIncludedIds(entityJsonNode);
    inclusionSubgraphSession.updateInclusionArcs(resource.getResourceId(), includedIds);
  }

  private static List<String> extractFirstLevelIncludedIds(JsonNode artifact) {
    List<String> linkIds = new ArrayList<>();
    JsonNode properties = artifact.get(JsonSchemaConstants.PROPERTIES);
    for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
      String fieldName = it.next();
      JsonNode embedded = null;
      if (!ModelUtil.isSpecialField(fieldName)) {
        JsonNode candidate = properties.get(fieldName);
        JsonNode typeNode = candidate.get(JsonSchemaConstants.TYPE);
        String typeValue = typeNode.textValue();
        if (JsonSchemaConstants.TYPE_VALUE_OBJECT.equals(typeValue)) {
          // single embedded artifact
          embedded = candidate;
        } else if (JsonSchemaConstants.TYPE_VALUE_ARRAY.equals(typeValue)) {
          // multi embedded artifact
          embedded = candidate.get(JsonSchemaConstants.ITEMS);
        }
      }
      if (embedded != null) {
        JsonNode atTypeNode = embedded.get(LinkedData.TYPE);
        String atType = atTypeNode.textValue();
        JsonNode atIdNode = embedded.get(LinkedData.ID);
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

  public static InclusionSubgraphResponse buildAffectedTree(InclusionSubgraphRequest treeRequest, InclusionSubgraphServiceSession inclusionSubgraphSession) {
    String rootId = treeRequest.getId();
    InclusionSubgraphResponse response = new InclusionSubgraphResponse();
    response.setId(rootId);
    response.setElements(computeAffectedElements(rootId, treeRequest.getElements(), inclusionSubgraphSession));
    response.setTemplates(computeAffectedTemplates(rootId, treeRequest.getTemplates(), inclusionSubgraphSession));

    return response;
  }

  private static Map<String, InclusionSubgraphTemplate> computeAffectedTemplates(String id, Map<String, InclusionSubgraphTemplate> requestTemplates,
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
        t.setOperation(inclusionSubgraphTemplate.getOperation());
      }
    }
    return templates;
  }

  private static Map<String, InclusionSubgraphElement> computeAffectedElements(String id, Map<String, InclusionSubgraphElement> requestElements,
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
        e.setOperation(inclusionSubgraphElement.getOperation());
        if (inclusionSubgraphElement.getOperation() == InclusionSubgraphNodeOperation.UPDATE) {
          e.setElements(computeAffectedElements(elementId, inclusionSubgraphElement.getElements(), inclusionSubgraphSession));
          e.setTemplates(computeAffectedTemplates(elementId, inclusionSubgraphElement.getTemplates(), inclusionSubgraphSession));

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

  private static void recursivelyUpdateElements(String sourceId, Map<String, InclusionSubgraphElement> elements, InclusionSubgraphTodoList todoList) {
    for (InclusionSubgraphElement element : elements.values()) {
      updateElement(sourceId, element, todoList);
      recursivelyUpdateElements(element.getId(), element.getElements(), todoList);
      updateTemplates(element.getId(), element.getTemplates(), todoList);
    }
  }

  private static void updateTemplates(String sourceId, Map<String, InclusionSubgraphTemplate> templates, InclusionSubgraphTodoList todoList) {
    for (InclusionSubgraphTemplate template : templates.values()) {
      updateTemplate(sourceId, template, todoList);
    }
  }

  private static void updateElement(String sourceId, InclusionSubgraphElement element, InclusionSubgraphTodoList todoList) {
    InclusionSubgraphTodoElement todo = new InclusionSubgraphTodoElement();
    todo.setSourceId(sourceId);
    todo.setTargetId(element.getId());
    todoList.addTodoElement(todo);
  }

  private static void updateTemplate(String sourceId, InclusionSubgraphTemplate template, InclusionSubgraphTodoList todoList) {
    InclusionSubgraphTodoElement todo = new InclusionSubgraphTodoElement();
    todo.setSourceId(sourceId);
    todo.setTargetId(template.getId());
    todoList.addTodoElement(todo);
  }


  public static boolean updateSubdocumentByAtId(JsonNode parentDocument, String idToBeReplaced, JsonNode newDocument) {
    return findAndReplaceDocumentNode(null, null, parentDocument, idToBeReplaced, newDocument);
  }

  private static boolean findAndReplaceDocumentNode(String key, ObjectNode parent, JsonNode currentNode, String idToBeReplaced, JsonNode newDocument) {
    if (currentNode.has(LinkedData.ID) && currentNode.get(LinkedData.ID).asText().equals(idToBeReplaced)) {
      if (parent != null) {
        parent.replace(key, newDocument);
      }
      return true;
    }

    Iterator<String> fieldNames = currentNode.fieldNames();
    while (fieldNames.hasNext()) {
      String childKey = fieldNames.next();
      JsonNode child = currentNode.get(childKey);
      if (child.isObject()) {
        boolean found = findAndReplaceDocumentNode(childKey, (ObjectNode) currentNode, child, idToBeReplaced, newDocument);
        if (found) {
          return true;
        }
      }
    }
    return false;
  }
}
