package org.metadatacenter.server.search.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.server.queue.util.PermissionQueueService;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;

import static org.metadatacenter.server.search.SearchPermissionQueueEventType.*;

public class SearchPermissionEnqueueService {

  private final PermissionQueueService queueService;

  public SearchPermissionEnqueueService(CedarConfig cedarConfig) {
    this.queueService = new PermissionQueueService(cedarConfig.getCacheConfig().getPersistent());
  }

  private void enqueue(String id, SearchPermissionQueueEventType eventType) {
    SearchPermissionQueueEvent event = new SearchPermissionQueueEvent(id, eventType);
    queueService.enqueueEvent(event);
  }

  public void resourceMoved(String id) {
    enqueue(id, RESOURCE_MOVED);
  }

  public void resourcePermissionsChanged(CedarFilesystemResourceId id) {
    // TODO: Check if this was a real change. Check this at the calling side
    enqueue(id.getId(), RESOURCE_PERMISSION_CHANGED);
  }

  public void folderMoved(String id) {
    enqueue(id, FOLDER_MOVED);
  }

  public void folderPermissionsChanged(CedarFilesystemResourceId id) {
    // TODO: Check if this was a real change. Check this at the calling side
    enqueue(id.getId(), FOLDER_PERMISSION_CHANGED);
  }

  public void groupMembersUpdated(String id) {
    enqueue(id, GROUP_MEMBERS_UPDATED);
  }

  public void groupDeleted(String id) {
    enqueue(id, GROUP_DELETED);
  }

}
