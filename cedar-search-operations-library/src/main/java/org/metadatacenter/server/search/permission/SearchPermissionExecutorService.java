package org.metadatacenter.server.search.permission;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.Upsert;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
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
    UserService userService = CedarDataServices.getInstance().getNeoUserService();
    CedarRequestContext context = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    this.nodeSearchingService = nodeSearchingService;
    this.nodeIndexingService = nodeIndexingService;
    this.indexUtils = indexUtils;
    this.cedarRequestContext = context;
    folderSession = CedarDataServices.getInstance().getFolderServiceSession(context);
    permissionSession = CedarDataServices.getInstance().getResourcePermissionServiceSession(context);
    categorySession = CedarDataServices.getInstance().getCategoryServiceSession(context);
  }

  SearchPermissionExecutorService(IndexUtils indexUtils, NodeSearchingService nodeSearchingService,
                                  NodeIndexingService nodeIndexingService, FolderServiceSession folderSession,
                                  ResourcePermissionServiceSession permissionSession,
                                  CategoryServiceSession categorySession, CedarRequestContext cedarRequestContext) {
    this.indexUtils = indexUtils;
    this.nodeSearchingService = nodeSearchingService;
    this.nodeIndexingService = nodeIndexingService;
    this.folderSession = folderSession;
    this.permissionSession = permissionSession;
    this.categorySession = categorySession;
    this.cedarRequestContext = cedarRequestContext;
  }

  // Main entry point
  public void handleEvent(SearchPermissionQueueEvent event) throws CedarProcessingException {
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

  private void updateOneArtifact(CedarArtifactId artifactId) throws CedarProcessingException {
    log.debug("Update one artifact:" + artifactId);
    // upsertOnePermissions resolves the artifact itself, and removes the index document when the
    // artifact no longer exists
    upsertOnePermissions(Upsert.UPDATE, artifactId);
  }

  private void updateFolderRecursively(CedarFolderId folderId) throws CedarProcessingException {
    log.debug("Update recursive folder:");
    List<FileSystemResource> subtree = folderSession.findAllDescendantNodesById(folderId);
    for (FileSystemResource n : subtree) {
      upsertOnePermissions(Upsert.UPDATE, n.getResourceId());
    }
  }

  private void updateAllByUpdatedGroup(CedarGroupId groupId) throws CedarProcessingException {
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

  private void updateAllByDeletedGroup(CedarGroupId groupId) throws CedarProcessingException {
    log.debug("Update all visible by group:");
    List<String> allCedarIdsForGroup = nodeSearchingService.findAllCedarIdsForGroup(groupId);
    for (String cid : allCedarIdsForGroup) {
      log.info("Need to update permissions for:" + cid);
      upsertOnePermissions(Upsert.UPDATE, CedarUntypedFilesystemResourceId.build(cid));
    }
  }

  private void upsertOnePermissions(Upsert upsert, CedarFilesystemResourceId resourceId)
      throws CedarProcessingException {
    log.debug("upsertOneDocument for permissions:" + upsert.getValue() + ":" + resourceId);
    FileSystemResource node = folderSession.findResourceById(resourceId);
    if (node == null) {
      // The resource is gone from the graph, so any document the index still holds for it is
      // an orphan: a permission update has nothing to write, and leaving the document in place
      // would keep the resource visible to search and keep feeding it back to this service,
      // which sources its work list from the index. Remove it instead.
      log.info("The resource no longer exists, removing it from the index:" + resourceId);
      nodeIndexingService.removeDocumentFromIndex(resourceId);
      return;
    }
    CedarNodeMaterializedPermissions perm = permissionSession.getResourceMaterializedPermission(resourceId);
    CedarNodeMaterializedCategories categories = null;
    if (node.getType() != CedarResourceType.FOLDER) {
      categories = categorySession.getArtifactMaterializedCategories(CedarUntypedArtifactId.build(resourceId.getId()));
    }
    if (upsert == Upsert.UPDATE) {
      nodeIndexingService.removeDocumentFromIndex(resourceId);
    }
    nodeIndexingService.indexDocument(node, perm, categories, cedarRequestContext);
  }
}
