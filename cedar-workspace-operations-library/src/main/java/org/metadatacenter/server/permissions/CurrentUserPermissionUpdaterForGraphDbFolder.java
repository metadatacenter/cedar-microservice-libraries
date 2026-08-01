package org.metadatacenter.server.permissions;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;

public class CurrentUserPermissionUpdaterForGraphDbFolder extends CurrentUserPermissionUpdater {

  private final ResourcePermissionServiceSession permissionSession;
  private final FolderWithCurrentUserPermissions folder;

  private CurrentUserPermissionUpdaterForGraphDbFolder(ResourcePermissionServiceSession permissionSession, FolderWithCurrentUserPermissions folder) {
    this.permissionSession = permissionSession;
    this.folder = folder;
  }

  public static CurrentUserPermissionUpdater get(ResourcePermissionServiceSession permissionSession, FolderWithCurrentUserPermissions folder) {
    return new CurrentUserPermissionUpdaterForGraphDbFolder(permissionSession, folder);
  }

  @Override
  public void update(CurrentUserResourcePermissions currentUserResourcePermissions) {
    CedarFilesystemResourceId id = folder.getResourceId();
    if (permissionSession.userHasWriteAccessToResource(id)) {
      currentUserResourcePermissions.setCanWrite(true);
      currentUserResourcePermissions.setCanDelete(true);
      currentUserResourcePermissions.setCanRead(true);
      if (!folder.isRoot() && !folder.isSystem() && !folder.isUserHome()) {
        currentUserResourcePermissions.setCanShare(true);
      }
      currentUserResourcePermissions.setCanMakeOpen(!folder.isOpen());
      currentUserResourcePermissions.setCanMakeNotOpen(folder.isOpen());
    } else if (permissionSession.userHasReadAccessToResource(id)) {
      currentUserResourcePermissions.setCanRead(true);
    }
    if (permissionSession.userCanChangeOwnerOfResource(id)) {
      currentUserResourcePermissions.setCanChangeOwner(true);
    }
  }
}
