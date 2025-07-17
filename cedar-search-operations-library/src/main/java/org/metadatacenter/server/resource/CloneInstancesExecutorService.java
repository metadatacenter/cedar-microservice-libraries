package org.metadatacenter.server.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang.CharEncoding;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarObjectNotFoundException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.*;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.GraphDbObjectBuilder;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.server.valuerecommender.ValuerecommenderReindexQueueService;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageActionType;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageResourceType;
import org.metadatacenter.util.CedarResourceTypeUtil;
import org.metadatacenter.util.ModelUtil;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.http.ProxyUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.metadatacenter.model.ModelNodeNames.*;

public class CloneInstancesExecutorService {

  private static final Logger log = LoggerFactory.getLogger(CloneInstancesExecutorService.class);

  private final FolderServiceSession folderSession;
  private final CedarRequestContext cedarRequestContext;
  protected final MicroserviceUrlUtil microserviceUrlUtil;

  protected static NodeIndexingService nodeIndexingService;
  protected static ValuerecommenderReindexQueueService valuerecommenderReindexQueueService;

  public CloneInstancesExecutorService(CedarConfig cedarConfig) {
    UserService userService = CedarDataServices.getNeoUserService();

    this.cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    folderSession = CedarDataServices.getFolderServiceSession(cedarRequestContext);
    microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
  }

  public static void injectServices(NodeIndexingService nodeIndexingService,
                                    ValuerecommenderReindexQueueService valuerecommenderReindexQueueService) {
    CloneInstancesExecutorService.nodeIndexingService = nodeIndexingService;
    CloneInstancesExecutorService.valuerecommenderReindexQueueService = valuerecommenderReindexQueueService;
  }

  // Main entry point
  public void handleEvent(CloneInstancesQueueEvent event) {
    cloneInstancesOfTemplate(CedarTemplateId.build(event.getOldId()), CedarTemplateId.build(event.getNewId()));
  }

  private void cloneInstancesOfTemplate(CedarTemplateId oldTemplateId, CedarTemplateId newTemplateId) {
    long numberOfInstances = folderSession.getNumberOfInstances(oldTemplateId);
    if (numberOfInstances > 0) {
      List<FolderServerResourceExtract> instanceExtracts =
          folderSession.searchIsBasedOn(List.of(CedarResourceType.INSTANCE), oldTemplateId, 1000, 0,
              List.of(QuerySortOptions.DEFAULT_SORT_FIELD.getName()));

      Map<String, List<FolderServerResourceExtract>> instancesByOwner = instanceExtracts.stream()
          .collect(Collectors.groupingBy(FolderServerResourceExtract::getOwnedBy));

      for (Map.Entry<String, List<FolderServerResourceExtract>> entry : instancesByOwner.entrySet()) {
        CedarUserId ownerUser = CedarUserId.build(entry.getKey());
        FolderServerFolder destinationFolder = folderSession.findHomeFolderOfUser(ownerUser);
        if (destinationFolder != null) {
          for (FolderServerResourceExtract instanceExtract : entry.getValue()) {
            try {
              copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId.build(instanceExtract.getId()),
                  oldTemplateId, newTemplateId,
                  destinationFolder.getResourceId());
            } catch (CedarException e) {
              log.error("Error when cloning instance:" + instanceExtract.getId(), e);
            }
          }
        } else {
          log.error("User:" + ownerUser + " has no home folder");
        }
      }
    }
  }


  private Response copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId oldInstanceId,
                                                       CedarTemplateId oldTemplateId,
                                                       CedarTemplateId newTemplateId,
                                                       CedarFolderId destinationFolderId) throws CedarException {
    CedarRequestContext c = this.cedarRequestContext;

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);
    CedarResourceType resourceType = CedarResourceType.INSTANCE;

    String originalDocument = null;
    try {
      String url = microserviceUrlUtil.getArtifact().getArtifactTypeWithId(resourceType, oldInstanceId);
      HttpResponse proxyResponse = ProxyUtil.proxyGet(url, c);
      HttpEntity entity = proxyResponse.getEntity();
      int statusCode = proxyResponse.getStatusLine().getStatusCode();
      if (entity != null) {
        originalDocument = EntityUtils.toString(entity, CharEncoding.UTF_8);
        JsonNode jsonNode = JsonMapper.MAPPER.readTree(originalDocument);
        ((ObjectNode) jsonNode).remove("@id");
        ((ObjectNode) jsonNode).put(SCHEMA_IS_BASED_ON, newTemplateId.getId());
        if (jsonNode.get(SCHEMA_ORG_IDENTIFIER) != null) {
          String schemaId = jsonNode.get(SCHEMA_ORG_IDENTIFIER).asText();
          // Since we are creating a copy, we remove the schema:identifier to avoid confusion with the original artifact
          ((ObjectNode) jsonNode).remove(SCHEMA_ORG_IDENTIFIER);
        }
        originalDocument = jsonNode.toString();
      }
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }

    try {
      String url = microserviceUrlUtil.getArtifact().getResourceType(resourceType);

      HttpResponse templateProxyResponse = ProxyUtil.proxyPost(url, c, originalDocument);

      int statusCode = templateProxyResponse.getStatusLine().getStatusCode();
      if (statusCode != HttpStatus.SC_CREATED) {
        // artifact was not created
        throw new CedarProcessingException("Error when creating artifact from template: " + statusCode);
      } else {
        // artifact was created
        HttpEntity entity = templateProxyResponse.getEntity();
        Header locationHeader = templateProxyResponse.getFirstHeader(HttpHeaders.LOCATION);
        String entityContent = EntityUtils.toString(entity, CharEncoding.UTF_8);
        JsonNode jsonNode = JsonMapper.MAPPER.readTree(entityContent);
        String createdId = jsonNode.get("@id").asText();
        CedarArtifactId newInstanceId = CedarArtifactId.build(createdId, resourceType);

        FolderServerArtifact folderServerCreatedResource =
            copyArtifactToFolderInGraphDb(c, oldInstanceId, newInstanceId, destinationFolderId, resourceType,
                ModelUtil.extractNameFromResource(resourceType, jsonNode).getValue(),
                ModelUtil.extractDescriptionFromResource(resourceType, jsonNode).getValue(),
                ModelUtil.extractIdentifierFromResource(resourceType, jsonNode).getValue(),
                newTemplateId);

        if (templateProxyResponse.getEntity() != null) {
          // index the artifact that has been created
          createIndexArtifact(folderServerCreatedResource, c);
          createValuerecommenderResource(folderServerCreatedResource);
          URI location = CedarUrlUtil.getLocationURI(templateProxyResponse);
          return Response.created(location).entity(templateProxyResponse.getEntity().getContent()).build();
        } else {
          return Response.ok().build();
        }
      }
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  //TODO: extract this, present in the CommandFileSystemResource as well
  protected void createIndexArtifact(FolderServerArtifact folderServerArtifact, CedarRequestContext c) throws CedarProcessingException {
    nodeIndexingService.indexDocument(folderServerArtifact, c);
  }

  //TODO: extract this, present in the CommandFileSystemResource as well
  protected void createValuerecommenderResource(FolderServerArtifact folderServerArtifact) {
    ValuerecommenderReindexMessage event = buildValuerecommenderEvent(folderServerArtifact,
        ValuerecommenderReindexMessageActionType.CREATED);
    if (event != null) {
      valuerecommenderReindexQueueService.enqueueEvent(event);
    }
  }

  //TODO: extract this, present in the CommandFileSystemResource as well
  private ValuerecommenderReindexMessage buildValuerecommenderEvent(FolderServerArtifact folderServerResource, ValuerecommenderReindexMessageActionType actionType) {
    ValuerecommenderReindexMessage event = null;
    if (folderServerResource.getType() == CedarResourceType.TEMPLATE) {
      CedarTemplateId templateId = CedarTemplateId.build(folderServerResource.getId());
      event = new ValuerecommenderReindexMessage(templateId, null, ValuerecommenderReindexMessageResourceType.TEMPLATE, actionType);
    } else if (folderServerResource.getType() == CedarResourceType.INSTANCE) {
      FolderServerInstance instance = (FolderServerInstance) folderServerResource;
      CedarTemplateInstanceId instanceId = CedarTemplateInstanceId.build(instance.getId());
      event = new ValuerecommenderReindexMessage(instance.getIsBasedOn(), instanceId, ValuerecommenderReindexMessageResourceType.INSTANCE,
          actionType);
    }
    return event;
  }


  //TODO: extract this, present in the CommandFileSystemResource as well
  private FolderServerArtifact copyArtifactToFolderInGraphDb(CedarRequestContext c, CedarArtifactId oldId,
                                                             CedarArtifactId newId,
                                                             CedarFolderId targetFolderId,
                                                             CedarResourceType resourceType, String name,
                                                             String description, String identifier, CedarTemplateId newTemplateId) throws CedarException {

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    if (CedarResourceTypeUtil.isNotValidForRestCall(resourceType)) {
      throw new CedarProcessingException("You passed an illegal resourceType:'" + resourceType.getValue() +
          "'. The allowed values are:" + CedarResourceTypeUtil.getValidResourceTypesForRestCalls()).badRequest()
          .errorKey(CedarErrorKey.INVALID_RESOURCE_TYPE)
          .parameter("invalidResourceTypes", resourceType.getValue())
          .parameter("allowedResourceTypes", CedarResourceTypeUtil.getValidResourceTypeValuesForRestCalls());
    }

    ResourceVersion version = ResourceVersion.ZERO_ZERO_ONE;
    BiboStatus publicationStatus = BiboStatus.DRAFT;

    // check existence of parent folder
    FolderServerArtifact newResource = null;
    FolderServerFolder parentFolder = folderSession.findFolderById(targetFolderId);

    String candidatePath = null;
    if (parentFolder == null) {
      throw new CedarObjectNotFoundException("The parent folder is not present!")
          .parameter("targetFolderId", targetFolderId)
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_FOUND);
    } else {
      // Later we will guarantee some kind of uniqueness for the artifact names
      // Currently we allow duplicate names, the id is the PK
      FolderServerArtifact oldResource = folderSession.findArtifactById(oldId);
      if (oldResource == null) {
        throw new CedarObjectNotFoundException("The source artifact was not found!")
            .parameter("@id", oldId)
            .parameter("resourceType", resourceType.getValue())
            .errorKey(CedarErrorKey.ARTIFACT_NOT_FOUND);
      } else {
        FolderServerArtifact brandNewResource = GraphDbObjectBuilder.forResourceType(resourceType, newId, name,
            description, identifier, version, publicationStatus);
        if (brandNewResource instanceof FolderServerSchemaArtifact schemaArtifact) {
          schemaArtifact.setLatestVersion(true);
          schemaArtifact.setLatestDraftVersion(publicationStatus == BiboStatus.DRAFT);
          schemaArtifact.setLatestPublishedVersion(publicationStatus == BiboStatus.PUBLISHED);
        }
        if (resourceType == CedarResourceType.INSTANCE) {
          ((FolderServerInstance) brandNewResource).setIsBasedOn(newTemplateId);
        }
        newResource = folderSession.createResourceAsChildOfId(brandNewResource, targetFolderId);
      }
    }

    if (newResource != null) {
      folderSession.setDerivedFrom(newId, oldId);
      return newResource;
    } else {
      throw new CedarProcessingException("The artifact was not created!")
          .parameter("@id", oldId)
          .parameter("targetFolderId", parentFolder)
          .errorKey(CedarErrorKey.RESOURCE_NOT_CREATED);
    }
  }

}
