package org.metadatacenter.search;

import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

import java.util.ArrayList;
import java.util.List;

public class IndexingDocumentDocument extends IndexedDocumentDocument {

  private List<String> groups;

  public IndexingDocumentDocument() {
  }

  public IndexingDocumentDocument(String cid) {
    this.cid = cid;
    resetUsers();
    resetGroups();
  }

  private void resetUsers() {
    users = new ArrayList<>();
  }

  private void resetGroups() {
    groups = new ArrayList<>();
  }

  public void setMaterializedPermissions(CedarNodeMaterializedPermissions permissions) {
    resetUsers();
    resetGroups();
    for (String userId : permissions.getUserRoles().keySet()) {
      ResourceRole role = permissions.getUserRoles().get(userId);
      users.add(CedarNodeMaterializedPermissions.getKey(userId, ResourceRole.VIEWER));
      if (role.includes(ResourceRole.EDITOR)) {
        users.add(CedarNodeMaterializedPermissions.getKey(userId, ResourceRole.EDITOR));
      }
      if (role.includes(ResourceRole.MANAGER)) {
        users.add(CedarNodeMaterializedPermissions.getKey(userId, ResourceRole.MANAGER));
      }
    }
    for (String groupId : permissions.getGroupRoles().keySet()) {
      ResourceRole role = permissions.getGroupRoles().get(groupId);
      groups.add(CedarNodeMaterializedPermissions.getKey(groupId, ResourceRole.VIEWER));
      if (role.includes(ResourceRole.EDITOR)) {
        groups.add(CedarNodeMaterializedPermissions.getKey(groupId, ResourceRole.EDITOR));
      }
      if (role.includes(ResourceRole.MANAGER)) {
        groups.add(CedarNodeMaterializedPermissions.getKey(groupId, ResourceRole.MANAGER));
      }
    }
    this.setComputedEverybodyPermission(permissions.getEverybodyPermission());
  }

  public List<String> getGroups() {
    return groups;
  }

  public void setMaterializedCategories(CedarNodeMaterializedCategories categories) {
    this.categories = new ArrayList<>();
    if (categories != null) {
      this.categories.addAll(categories.getCategoryIds());
    }
  }

}
