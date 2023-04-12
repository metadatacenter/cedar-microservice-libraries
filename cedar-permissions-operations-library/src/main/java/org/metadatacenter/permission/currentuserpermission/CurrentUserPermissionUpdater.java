package org.metadatacenter.permission.currentuserpermission;

import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;

public abstract class CurrentUserPermissionUpdater {

  public abstract void update(CurrentUserResourcePermissions currentUserResourcePermissions);
}
