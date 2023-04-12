package org.metadatacenter.permission.currentuserpermission;

import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;

public abstract class CurrentUserCategoryPermissionUpdater {

  public abstract void update(CurrentUserCategoryPermissions currentUserCatergoryPermissions);
}
