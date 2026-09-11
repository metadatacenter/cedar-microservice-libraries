package org.metadatacenter.server.resource;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarObjectNotFoundException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.GraphDbObjectBuilder;
import org.metadatacenter.model.ModelNodeNames;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.valuerecommender.ValuerecommenderReindexQueueService;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageActionType;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageResourceType;
import org.metadatacenter.util.CedarResourceTypeUtil;
import org.metadatacenter.util.ModelUtil;

/** Shared operations used by both synchronous copies and clone jobs. */
public final class ArtifactCopyOperations {

  /**
   * Makes a document read from the artifact server fit to be created as a new artifact.
   *
   * Every derived write starts as the artifact it derives from: a copy, a clone of an instance onto
   * a new template, the draft of a published version. Each has to stop being that artifact before
   * it is sent, and two of the steps are not conveniences but rules the artifact server enforces.
   * The identifier key must be present carrying null, because the server assigns identifiers and an
   * absent key cannot be told from a forgotten one. A DOI belongs to the artifact it was minted
   * for and never to something derived from it.
   *
   * What distinguishes the three — the template a clone now points at, the provenance a copy
   * records, the version and status a draft takes — belongs to the caller that knows it. This is
   * only what none of them may skip, in one place because they did not all get it right: when the
   * identifier rule arrived, the two callers in the resource server were updated in the same hour
   * and the one in this library was not, so copying a published template's instances failed on
   * every instance for three weeks, silently, on the worker queue.
   */
  public static ObjectNode prepareDerivedBody(ObjectNode document) {
    document.putNull(ModelNodeNames.JSON_LD_ID);
    ModelUtil.removeDOIFromResource(document);
    return document;
  }

  public static FolderServerArtifact registerCopy(FolderServiceSession folderSession,
                                                  CedarArtifactId oldId,
                                                  CedarArtifactId newId,
                                                  CedarFolderId targetFolderId,
                                                  CedarResourceType resourceType,
                                                  String name,
                                                  String description,
                                                  String identifier,
                                                  CedarTemplateId instanceTemplateOverride,
                                                  CedarUserId ownerOverride) throws CedarException {
    if (CedarResourceTypeUtil.isNotValidForRestCall(resourceType)) {
      throw new CedarProcessingException("You passed an illegal resourceType:'" + resourceType.getValue() +
          "'. The allowed values are:" + CedarResourceTypeUtil.getValidResourceTypesForRestCalls()).badRequest()
          .errorKey(CedarErrorKey.INVALID_RESOURCE_TYPE)
          .parameter("invalidResourceTypes", resourceType.getValue())
          .parameter("allowedResourceTypes", CedarResourceTypeUtil.getValidResourceTypeValuesForRestCalls());
    }

    FolderServerFolder parentFolder = folderSession.findFolderById(targetFolderId);
    if (parentFolder == null) {
      throw new CedarObjectNotFoundException("The parent folder is not present!")
          .parameter("targetFolderId", targetFolderId)
          .errorKey(CedarErrorKey.PARENT_FOLDER_NOT_FOUND);
    }

    FolderServerArtifact oldResource = folderSession.findArtifactById(oldId);
    if (oldResource == null) {
      throw new CedarObjectNotFoundException("The source artifact was not found!")
          .parameter("@id", oldId)
          .parameter("resourceType", resourceType.getValue())
          .errorKey(CedarErrorKey.ARTIFACT_NOT_FOUND);
    }

    BiboStatus publicationStatus = BiboStatus.DRAFT;
    FolderServerArtifact newResource = GraphDbObjectBuilder.forResourceType(resourceType, newId, name,
        description, identifier, ResourceVersion.ZERO_ZERO_ONE, publicationStatus);
    if (newResource instanceof FolderServerSchemaArtifact schemaArtifact) {
      schemaArtifact.setLatestVersion(true);
      schemaArtifact.setLatestDraftVersion(true);
      schemaArtifact.setLatestPublishedVersion(false);
    }
    if (newResource instanceof FolderServerInstance instance) {
      CedarTemplateId templateId = instanceTemplateOverride != null
          ? instanceTemplateOverride
          : ((FolderServerInstance) oldResource).getIsBasedOn();
      instance.setIsBasedOn(templateId);
    }

    FolderServerArtifact createdResource = ownerOverride == null
        ? folderSession.createResourceAsChildOfId(newResource, targetFolderId)
        : folderSession.createResourceAsChildOfId(newResource, targetFolderId, ownerOverride);
    if (createdResource == null) {
      throw new CedarProcessingException("The artifact was not created!")
          .parameter("@id", oldId)
          .parameter("targetFolderId", parentFolder)
          .errorKey(CedarErrorKey.RESOURCE_NOT_CREATED);
    }

    folderSession.setDerivedFrom(newId, oldId);
    return createdResource;
  }

  public static void indexCreatedArtifact(NodeIndexingService indexingService,
                                          FolderServerArtifact artifact,
                                          CedarRequestContext context) throws CedarProcessingException {
    indexingService.indexDocument(artifact, context);
  }

  public static void enqueueValuerecommenderUpdate(ValuerecommenderReindexQueueService queueService,
                                                   FolderServerArtifact artifact,
                                                   ValuerecommenderReindexMessageActionType actionType) {
    ValuerecommenderReindexMessage event = buildValuerecommenderEvent(artifact, actionType);
    if (event != null) {
      queueService.enqueueEvent(event);
    }
  }

  /** Returns false only when an applicable event could not be persisted to the Redis queue. */
  public static boolean enqueueValuerecommenderUpdateWithResult(
      ValuerecommenderReindexQueueService queueService,
      FolderServerArtifact artifact,
      ValuerecommenderReindexMessageActionType actionType) {
    ValuerecommenderReindexMessage event = buildValuerecommenderEvent(artifact, actionType);
    return event == null || queueService.enqueueEventWithResult(event);
  }

  private static ValuerecommenderReindexMessage buildValuerecommenderEvent(
      FolderServerArtifact artifact, ValuerecommenderReindexMessageActionType actionType) {
    if (artifact.getType() == CedarResourceType.TEMPLATE) {
      return new ValuerecommenderReindexMessage(CedarTemplateId.build(artifact.getId()), null,
          ValuerecommenderReindexMessageResourceType.TEMPLATE, actionType);
    }
    if (artifact.getType() == CedarResourceType.INSTANCE) {
      FolderServerInstance instance = (FolderServerInstance) artifact;
      return new ValuerecommenderReindexMessage(instance.getIsBasedOn(),
          CedarTemplateInstanceId.build(instance.getId()),
          ValuerecommenderReindexMessageResourceType.INSTANCE, actionType);
    }
    return null;
  }

  private ArtifactCopyOperations() {
  }
}
