package org.metadatacenter.server.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.server.valuerecommender.ValuerecommenderReindexQueueService;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageActionType;
import org.metadatacenter.util.ModelUtil;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.http.HttpTimeouts;
import org.metadatacenter.util.http.ProxyUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.model.ModelNodeNames.SCHEMA_IS_BASED_ON;
import static org.metadatacenter.model.ModelNodeNames.SCHEMA_ORG_IDENTIFIER;

public class CloneInstancesExecutorService {

  private static final Logger log = LoggerFactory.getLogger(CloneInstancesExecutorService.class);

  private final FolderServiceSession folderSession;
  private final CedarRequestContext cedarRequestContext;
  protected final MicroserviceUrlUtil microserviceUrlUtil;
  protected final LinkedDataUtil linkedDataUtil;

  protected static NodeIndexingService nodeIndexingService;
  protected static ValuerecommenderReindexQueueService valuerecommenderReindexQueueService;

  public CloneInstancesExecutorService(CedarConfig cedarConfig) {
    UserService userService = CedarDataServices.getInstance().getNeoUserService();

    cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    folderSession = CedarDataServices.getInstance().getFolderServiceSession(cedarRequestContext);
    microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
    linkedDataUtil = cedarRequestContext.getLinkedDataUtil();
  }

  CloneInstancesExecutorService(FolderServiceSession folderSession,
                                CedarRequestContext cedarRequestContext,
                                MicroserviceUrlUtil microserviceUrlUtil,
                                LinkedDataUtil linkedDataUtil) {
    this.folderSession = folderSession;
    this.cedarRequestContext = cedarRequestContext;
    this.microserviceUrlUtil = microserviceUrlUtil;
    this.linkedDataUtil = linkedDataUtil;
  }

  public static void injectServices(NodeIndexingService nodeIndexingService,
                                    ValuerecommenderReindexQueueService valuerecommenderReindexQueueService) {
    CloneInstancesExecutorService.nodeIndexingService = nodeIndexingService;
    CloneInstancesExecutorService.valuerecommenderReindexQueueService = valuerecommenderReindexQueueService;
  }

  // Main entry point
  public void handleEvent(CloneInstancesQueueEvent event) throws CedarException {
    cloneInstancesOfTemplate(CedarTemplateId.build(event.getOldId()), CedarTemplateId.build(event.getNewId()),
        event.getNewFolderName());
  }

  void cloneInstancesOfTemplate(CedarTemplateId oldTemplateId, CedarTemplateId newTemplateId,
                                String newFolderName) throws CedarException {
    List<String> failedInstanceIds = new ArrayList<>();
    // Cloning mutates as it goes, so once anything has been created a failure must not be retried:
    // the queue processor would re-run the whole event and duplicate every copy that succeeded.
    boolean mutated = false;
    long numberOfInstances = folderSession.getNumberOfInstances(oldTemplateId);
    if (numberOfInstances > 0) {
      List<FolderServerResourceExtract> instanceExtracts = findAllInstances(folderSession, oldTemplateId);

      instanceExtracts.stream()
          .filter(instance -> instance.getOwnedBy() == null || instance.getOwnedBy().isBlank())
          .map(FolderServerResourceExtract::getId)
          .forEach(failedInstanceIds::add);

      Map<String, List<FolderServerResourceExtract>> instancesByOwner = groupInstancesByOwner(instanceExtracts);

      try {
        for (Map.Entry<String, List<FolderServerResourceExtract>> entry : instancesByOwner.entrySet()) {
          CedarUserId ownerUser = CedarUserId.build(entry.getKey());
          FolderServerFolder homeFolder = folderSession.findHomeFolderOfUser(ownerUser);
          if (homeFolder != null) {
            FolderServerTemplate newTemplate = (FolderServerTemplate) folderSession.findResourceById(newTemplateId);
            CedarFolderId newTargetFolderId = linkedDataUtil.buildNewLinkedDataIdObject(CedarFolderId.class);
            FolderServerFolder newFolder = new FolderServerFolder();

            if (newFolderName != null && !newFolderName.trim().isEmpty()) {
              newFolder.setName(newFolderName.trim());
            } else {
              newFolder.setName(newTemplate.getName() + " v " + newTemplate.getVersion().getValue() + " cloned instances");
            }

            newFolder.setDescription("Automatically created folder");

            FolderServerFolder newTargetFolder = folderSession.createFolderAsChildOfId(newFolder,
                homeFolder.getResourceId(), newTargetFolderId, ownerUser);
            mutated = true;
            for (FolderServerResourceExtract instanceExtract : entry.getValue()) {
              try {
                copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId.build(instanceExtract.getId()),
                    newTemplateId,
                    newTargetFolder.getResourceId(), ownerUser);
              } catch (CedarException e) {
                log.error("Error when cloning instance:" + instanceExtract.getId(), e);
                failedInstanceIds.add(instanceExtract.getId());
              }
            }
          } else {
            log.error("User:" + ownerUser + " has no home folder");
            entry.getValue().stream()
                .map(FolderServerResourceExtract::getId)
                .forEach(failedInstanceIds::add);
          }
        }
      } catch (Exception e) {
        if (mutated) {
          throw new CloneInstancesNotRetryableException("Cloning the instances of " + oldTemplateId.getId()
              + " failed after part of the clone was created; re-running the event would duplicate what "
              + "succeeded.", e);
        }
        if (e instanceof CedarException cedarException) {
          throw cedarException;
        }
        throw new CedarProcessingException(e);
      }
    }
    if (!failedInstanceIds.isEmpty()) {
      // Not retryable either way: the failures are per-instance and deterministic, and any copies
      // that did succeed would be duplicated by a re-run.
      throw new CloneInstancesNotRetryableException(
          "Failed to clone instances: " + String.join(", ", failedInstanceIds))
          .errorKey(CedarErrorKey.RESOURCE_NOT_CREATED)
          .parameter("failedInstanceCount", failedInstanceIds.size())
          .parameter("failedInstanceIds", failedInstanceIds);
    }
  }

  static List<FolderServerResourceExtract> findAllInstances(FolderServiceSession folderSession,
                                                            CedarTemplateId templateId) {
    final int pageSize = 1000;
    int offset = 0;
    List<FolderServerResourceExtract> instances = new ArrayList<>();
    List<FolderServerResourceExtract> page;
    do {
      page = folderSession.searchIsBasedOn(List.of(CedarResourceType.INSTANCE), templateId, pageSize, offset,
          List.of(QuerySortOptions.DEFAULT_SORT_FIELD.getName()));
      instances.addAll(page);
      offset += page.size();
    } while (page.size() == pageSize);
    return instances;
  }

  static Map<String, List<FolderServerResourceExtract>> groupInstancesByOwner(
      List<FolderServerResourceExtract> instances) {
    Map<String, List<FolderServerResourceExtract>> instancesByOwner = new LinkedHashMap<>();
    for (FolderServerResourceExtract instance : instances) {
      String owner = instance.getOwnedBy();
      if (owner == null || owner.isBlank()) {
        log.error("Instance has no owner and cannot be cloned: " + instance.getId());
        continue;
      }
      instancesByOwner.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(instance);
    }
    return instancesByOwner;
  }


  protected Response copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId oldInstanceId,
                                                         CedarTemplateId newTemplateId,
                                                         CedarFolderId destinationFolderId,
                                                         CedarUserId userId) throws CedarException {
    CedarRequestContext c = this.cedarRequestContext;

    FolderServiceSession folderSession = CedarDataServices.getInstance().getFolderServiceSession(c);
    CedarResourceType resourceType = CedarResourceType.INSTANCE;

    String originalDocument = null;
    try {
      String url = microserviceUrlUtil.getArtifact().getArtifactTypeWithId(resourceType, oldInstanceId);
      ClassicHttpResponse proxyResponse = ProxyUtil.proxyGet(url, c, HttpTimeouts.BATCH);
      HttpEntity entity = proxyResponse.getEntity();
      int statusCode = proxyResponse.getCode();
      if (entity != null) {
        originalDocument = EntityUtils.toString(entity, StandardCharsets.UTF_8);
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

      ClassicHttpResponse templateProxyResponse = ProxyUtil.proxyPost(url, c, originalDocument, HttpTimeouts.BATCH);

      int statusCode = templateProxyResponse.getCode();
      if (statusCode != HttpStatus.SC_CREATED) {
        // artifact was not created
        throw new CedarProcessingException("Error when creating artifact from template: " + statusCode);
      } else {
        // artifact was created
        HttpEntity entity = templateProxyResponse.getEntity();
        Header locationHeader = templateProxyResponse.getFirstHeader(HttpHeaders.LOCATION);
        String entityContent = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        JsonNode jsonNode = JsonMapper.MAPPER.readTree(entityContent);
        String createdId = jsonNode.get("@id").asText();
        CedarArtifactId newInstanceId = CedarArtifactId.build(createdId, resourceType);

        FolderServerArtifact folderServerCreatedResource =
            ArtifactCopyOperations.registerCopy(folderSession, oldInstanceId, newInstanceId,
                destinationFolderId, resourceType,
                ModelUtil.extractNameFromResource(resourceType, jsonNode).getValue(),
                ModelUtil.extractDescriptionFromResource(resourceType, jsonNode).getValue(),
                ModelUtil.extractIdentifierFromResource(resourceType, jsonNode).getValue(),
                newTemplateId,
                userId);

        if (templateProxyResponse.getEntity() != null) {
          // index the artifact that has been created
          ArtifactCopyOperations.indexCreatedArtifact(nodeIndexingService, folderServerCreatedResource, c);
          ArtifactCopyOperations.enqueueValuerecommenderUpdate(valuerecommenderReindexQueueService,
              folderServerCreatedResource, ValuerecommenderReindexMessageActionType.CREATED);
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

}
