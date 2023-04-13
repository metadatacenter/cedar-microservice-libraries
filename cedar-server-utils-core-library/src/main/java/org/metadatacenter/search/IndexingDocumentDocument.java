package org.metadatacenter.search;

import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

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
    for (String userId : permissions.getUserPermissions().keySet()) {
      FilesystemResourcePermission nodePermission = permissions.getUserPermissions().get(userId);
      users.add(CedarNodeMaterializedPermissions.getKey(userId, FilesystemResourcePermission.READ));
      if (nodePermission.equals(nodePermission.WRITE)) {
        users.add(CedarNodeMaterializedPermissions.getKey(userId, FilesystemResourcePermission.WRITE));
      }
    }
    for (String groupId : permissions.getGroupPermissions().keySet()) {
      FilesystemResourcePermission nodePermission = permissions.getGroupPermissions().get(groupId);
      groups.add(CedarNodeMaterializedPermissions.getKey(groupId, FilesystemResourcePermission.READ));
      if (nodePermission.equals(FilesystemResourcePermission.WRITE)) {
        groups.add(CedarNodeMaterializedPermissions.getKey(groupId, FilesystemResourcePermission.WRITE));
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
