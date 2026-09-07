package org.metadatacenter.server.permissions;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceActionPolicy;

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
    ResourceAuthority authority = permissionSession.getResourceAuthority(id);
    currentUserResourcePermissions.applyAccess(authority, permissionSession.getResourceCapabilities(id));
    currentUserResourcePermissions.setAvailableActions(ResourceActionPolicy.evaluate(
        CedarResourceType.FOLDER, currentUserResourcePermissions.getCapabilities(), folder.isOpen()));
  }
}
