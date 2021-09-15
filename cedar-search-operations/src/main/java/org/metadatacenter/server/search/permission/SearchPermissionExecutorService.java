package org.metadatacenter.server.search.permission;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.Upsert;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.search.util.IndexUtils;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SearchPermissionExecutorService {

  private static final Logger log = LoggerFactory.getLogger(SearchPermissionExecutorService.class);

  private final FolderServiceSession folderSession;
  private final ResourcePermissionServiceSession permissionSession;
  private final CategoryServiceSession categorySession;
  private final NodeSearchingService nodeSearchingService;
  private final NodeIndexingService nodeIndexingService;
  private final IndexUtils indexUtils;
  private final CedarRequestContext cedarRequestContext;

  public SearchPermissionExecutorService(CedarConfig cedarConfig, IndexUtils indexUtils, NodeSearchingService nodeSearchingService,
                                         NodeIndexingService nodeIndexingService) {
    UserService userService = CedarDataServices.getNeoUserService();
    this.nodeSearchingService = nodeSearchingService;
    this.nodeIndexingService = nodeIndexingService;
    this.indexUtils = indexUtils;

    this.cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);

    folderSession = CedarDataServices.getFolderServiceSession(cedarRequestContext);
    permissionSession = CedarDataServices.getResourcePermissionServiceSession(cedarRequestContext);
    categorySession = CedarDataServices.getCategoryServiceSession(cedarRequestContext);
  }

  // Main entry point
  public void handleEvent(SearchPermissionQueueEvent event) {
    switch (event.getEventType()) {
      case RESOURCE_MOVED:
        updateOneArtifact(CedarUntypedArtifactId.build(event.getId()));
        break;
      case RESOURCE_PERMISSION_CHANGED:
        updateOneArtifact(CedarUntypedArtifactId.build(event.getId()));
        break;
      case FOLDER_MOVED:
        updateFolderRecursively(CedarFolderId.build(event.getId()));
        break;
      case FOLDER_PERMISSION_CHANGED:
        updateFolderRecursively(CedarFolderId.build(event.getId()));
        break;
      case GROUP_MEMBERS_UPDATED:
        updateAllByUpdatedGroup(CedarGroupId.build(event.getId()));
        break;
      case GROUP_DELETED:
        updateAllByDeletedGroup(CedarGroupId.build(event.getId()));
        break;
    }
  }

  private void updateOneArtifact(CedarArtifactId artifactId) {
    FolderServerArtifact resource = folderSession.findArtifactById(artifactId);
    if (resource != null) {
      log.debug("Update one artifact:" + resource.getName());
      upsertOnePermissions(Upsert.UPDATE, artifactId);
    } else {
      log.error("Resource was not found:" + artifactId);
    }
  }

  private void updateFolderRecursively(CedarFolderId folderId) {
    log.debug("Update recursive folder:");
    List<FileSystemResource> subtree = folderSession.findAllDescendantNodesById(folderId);
    for (FileSystemResource n : subtree) {
      upsertOnePermissions(Upsert.UPDATE, n.getResourceId());
    }
  }

  private void updateAllByUpdatedGroup(CedarGroupId groupId) {
    log.debug("Update all visible by group:");
    List<FileSystemResource> collection = folderSession.findAllNodesVisibleByGroupId(groupId);
    for (FileSystemResource n : collection) {
      if (indexUtils.needsIndexing(n)) {
        upsertOnePermissions(Upsert.UPDATE, n.getResourceId());
      } else {
        log.info("The resource was skipped from indexing:" + n.getId());
      }
    }
  }

  private void updateAllByDeletedGroup(CedarGroupId groupId) {
    log.debug("Update all visible by group:");
    List<String> allCedarIdsForGroup = null;
    try {
      allCedarIdsForGroup = nodeSearchingService.findAllCedarIdsForGroup(groupId);
      for (String cid : allCedarIdsForGroup) {
        log.info("Need to update permissions for:" + cid);
        upsertOnePermissions(Upsert.UPDATE, CedarUntypedFilesystemResourceId.build(cid));
      }
    } catch (CedarProcessingException e) {
      log.error("Error while retrieving all the affected documents for group:" + groupId);
      return;
    }
  }

  private void upsertOnePermissions(Upsert upsert, CedarFilesystemResourceId resourceId) {
    log.debug("upsertOneDocument for permissions:" + upsert.getValue() + ":" + resourceId);
    try {
      FileSystemResource node = folderSession.findResourceById(resourceId);
      CedarNodeMaterializedPermissions perm = permissionSession.getResourceMaterializedPermission(resourceId);
      CedarNodeMaterializedCategories categories = null;
      if (node != null && node.getType() != CedarResourceType.FOLDER) {
        categories = categorySession.getArtifactMaterializedCategories(CedarUntypedArtifactId.build(resourceId.getId()));
      }
      if (upsert == Upsert.UPDATE) {
        nodeIndexingService.removeDocumentFromIndex(resourceId);
      }
      nodeIndexingService.indexDocument(node, perm, categories, cedarRequestContext,false);
    } catch (Exception e) {
      log.error("Error while upserting permissions", e);
    }
  }
}
