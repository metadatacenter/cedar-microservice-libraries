package org.metadatacenter.search;

import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

public class IdNodePermissionPair {

  private String id;
  private FilesystemResourcePermission permission;

  public IdNodePermissionPair() {
  }

  public IdNodePermissionPair(String id, FilesystemResourcePermission permission) {
    this.id = id;
    this.permission = permission;
  }

  public String getId() {
    return id;
  }

  public FilesystemResourcePermission getPermission() {
    return permission;
  }
}
