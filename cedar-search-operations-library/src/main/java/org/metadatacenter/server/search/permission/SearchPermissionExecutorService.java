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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class SearchPermissionExecutorService implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SearchPermissionExecutorService.class);

  private final ExecutorService projectionWorkers = Executors.newFixedThreadPool(8, runnable -> {
    Thread thread = new Thread(runnable, "search-permission-projection");
    thread.setDaemon(true);
    return thread;
  });

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

  public void handleEvent(SearchPermissionQueueEvent event) throws CedarProcessingException {
    handleEvents(List.of(event));
  }

  /** Events request a current graph projection, so overlapping targets need only one update. */
  public void handleEvents(List<SearchPermissionQueueEvent> events) throws CedarProcessingException {
    java.util.Map<String, CedarFilesystemResourceId> targets = new java.util.LinkedHashMap<>();
    for (SearchPermissionQueueEvent event : events) {
      switch (event.getEventType()) {
        case RESOURCE_MOVED, RESOURCE_PERMISSION_CHANGED -> {
          var id = CedarUntypedArtifactId.build(event.getId());
          targets.putIfAbsent(id.getId(), id);
        }
        case FOLDER_MOVED, FOLDER_PERMISSION_CHANGED -> {
          for (var resource : folderSession.findAllDescendantNodesById(CedarFolderId.build(event.getId()))) {
            targets.putIfAbsent(resource.getId(), resource.getResourceId());
          }
        }
        case GROUP_MEMBERS_UPDATED -> {
          for (var resource : folderSession.findAllNodesVisibleByGroupId(CedarGroupId.build(event.getId()))) {
            if (indexUtils.needsIndexing(resource)) targets.putIfAbsent(resource.getId(), resource.getResourceId());
          }
        }
        case GROUP_DELETED -> {
          for (String id : nodeSearchingService.findAllCedarIdsForGroup(CedarGroupId.build(event.getId()))) {
            targets.putIfAbsent(id, CedarUntypedFilesystemResourceId.build(id));
          }
        }
      }
    }
    if (targets.size() == 1) {
      upsertOnePermissions(Upsert.UPDATE, targets.values().iterator().next());
    } else {
      projectResources(new ArrayList<>(targets.values()));
    }
  }

  /** Independent resources may run together, but the next event cannot overtake this one. */
  private void projectResources(List<CedarFilesystemResourceId> resourceIds) throws CedarProcessingException {
    List<CedarFilesystemResourceId> distinct = new ArrayList<>(new LinkedHashSet<>(resourceIds));
    var completion = new java.util.concurrent.ExecutorCompletionService<NodeIndexingService.PermissionProjection>(projectionWorkers);
    var pending = distinct.iterator();
    java.util.Set<java.util.concurrent.Future<NodeIndexingService.PermissionProjection>> started = new java.util.HashSet<>();
    List<NodeIndexingService.PermissionProjection> prepared = new ArrayList<>();
    List<java.util.concurrent.Future<Void>> writes = new ArrayList<>();
    int active = 0;
    ExecutionException failure = null;
    try {
      while (pending.hasNext() || active > 0) {
        while (failure == null && active < 8 && pending.hasNext()) {
          var id = pending.next();
          started.add(completion.submit(() -> projectOne(id, true)));
          active++;
        }
        if (active == 0) break;
        try {
          var finished = completion.take();
          started.remove(finished);
          var projection = finished.get();
          if (projection != null && failure == null) prepared.add(projection);
          if (prepared.size() >= 128 && failure == null) {
            submitPermissionBatches(prepared, writes);
            prepared.clear();
          }
        } catch (ExecutionException e) {
          if (failure == null) failure = e;
        }
        active--;
      }
      if (failure == null && !prepared.isEmpty()) {
        try {
          submitPermissionBatches(prepared, writes);
        } catch (ExecutionException e) {
          failure = e;
        }
      }
      // A failed bulk must not let its still-running peers overwrite the next event's projection.
      for (var write : writes) {
        try {
          write.get();
        } catch (ExecutionException e) {
          if (failure == null) failure = e;
        }
      }
      if (failure != null) throw new CedarProcessingException(failure);
    } catch (InterruptedException e) {
      for (var future : started) future.cancel(true);
      for (var write : writes) write.cancel(true);
      Thread.currentThread().interrupt();
      throw new CedarProcessingException(e);
    }
  }

  private void submitPermissionBatches(List<NodeIndexingService.PermissionProjection> prepared,
      List<java.util.concurrent.Future<Void>> writes) throws InterruptedException, ExecutionException {
    for (int offset = 0; offset < prepared.size(); offset += 32) {
      if (writes.size() >= 4) {
        writes.get(0).get();
        writes.remove(0);
      }
      var batch = List.copyOf(prepared.subList(offset, Math.min(offset + 32, prepared.size())));
      writes.add(projectionWorkers.submit(() -> {
        nodeIndexingService.updatePermissionProjections(batch);
        return null;
      }));
    }
  }

  @Override
  public void close() throws InterruptedException {
    projectionWorkers.shutdownNow();
    projectionWorkers.awaitTermination(5, TimeUnit.SECONDS);
  }

  private void upsertOnePermissions(Upsert upsert, CedarFilesystemResourceId resourceId)
      throws CedarProcessingException {
    projectOne(resourceId, false);
  }

  private NodeIndexingService.PermissionProjection projectOne(CedarFilesystemResourceId resourceId, boolean prepare)
      throws CedarProcessingException {
    FileSystemResource node = folderSession.findResourceById(resourceId);
    if (node == null) {
      // The resource is gone from the graph, so any document the index still holds for it is
      // an orphan: a permission update has nothing to write, and leaving the document in place
      // would keep the resource visible to search and keep feeding it back to this service,
      // which sources its work list from the index. Remove it instead.
      log.info("The resource no longer exists, removing it from the index:" + resourceId);
      nodeIndexingService.removeDocumentFromIndex(resourceId);
      return null;
    }
    CedarNodeMaterializedPermissions perm = permissionSession.getResourceMaterializedPermission(node);
    CedarNodeMaterializedCategories categories = null;
    if (node.getType() != CedarResourceType.FOLDER) {
      categories = categorySession.getArtifactMaterializedCategories(CedarUntypedArtifactId.build(resourceId.getId()));
    }
    if (prepare) return nodeIndexingService.preparePermissionProjection(node, perm, categories, cedarRequestContext);
    nodeIndexingService.updatePermissionProjection(node, perm, categories, cedarRequestContext);
    return null;
  }
}
