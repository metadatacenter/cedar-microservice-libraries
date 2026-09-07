package org.metadatacenter.server.permissions;

import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.permission.currentuserpermission.CurrentUserCategoryPermissionUpdater;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryWithCurrentUserPermissions;

public class CurrentUserPermissionUpdaterForGraphDbCategory extends CurrentUserCategoryPermissionUpdater {

  private final CategoryPermissionServiceSession categoryPermissionSession;
  private final CategoryWithCurrentUserPermissions category;

  private CurrentUserPermissionUpdaterForGraphDbCategory(CategoryPermissionServiceSession categoryPermissionSession,
                                                         CategoryWithCurrentUserPermissions category) {
    this.categoryPermissionSession = categoryPermissionSession;
    this.category = category;
  }

  public static CurrentUserCategoryPermissionUpdater get(CategoryPermissionServiceSession categoryPermissionSession,
                                                         CategoryWithCurrentUserPermissions category) {
    return new CurrentUserPermissionUpdaterForGraphDbCategory(categoryPermissionSession, category);
  }

  @Override
  public void update(CurrentUserCategoryPermissions currentUserCategoryPermissions) {
    String id = category.getId();
    CedarCategoryId ccId = CedarCategoryId.build(id);
    CategoryAuthority authority = categoryPermissionSession.getCategoryAuthority(ccId);
    currentUserCategoryPermissions.applyAccess(
        authority, categoryPermissionSession.getCategoryCapabilities(ccId));
  }
}
