package org.metadatacenter.server.search;

public enum SearchPermissionQueueEventType {
  RESOURCE_MOVED,
  RESOURCE_PERMISSION_CHANGED,

  FOLDER_MOVED,
  FOLDER_PERMISSION_CHANGED,

  GROUP_MEMBERS_UPDATED,
  GROUP_DELETED
}
