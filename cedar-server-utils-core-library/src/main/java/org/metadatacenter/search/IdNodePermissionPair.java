package org.metadatacenter.search;

import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

public class IdNodePermissionPair {

  private String id;
  private ResourceRole role;

  public IdNodePermissionPair() {
  }

  public IdNodePermissionPair(String id, ResourceRole role) {
    this.id = id;
    this.role = role;
  }

  public String getId() {
    return id;
  }

  public ResourceRole getRole() {
    return role;
  }
}
