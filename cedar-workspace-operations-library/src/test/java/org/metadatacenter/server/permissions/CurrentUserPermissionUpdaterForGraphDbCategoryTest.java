package org.metadatacenter.server.permissions;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryCapability;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;
import org.metadatacenter.server.security.model.permission.category.CategoryWithCurrentUserPermissions;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Decision matrix for category authority projected onto a category report. */
class CurrentUserPermissionUpdaterForGraphDbCategoryTest {

  private static final String CATEGORY_ID = "category-1";
  private static final CedarCategoryId TYPED_ID = CedarCategoryId.build(CATEGORY_ID);

  @Test
  void projectionCarriesRoleOwnershipAndEffectiveCapabilities() {
    Fixture f = new Fixture();
    CategoryAuthority authority = new CategoryAuthority(CategoryRole.EDITOR, false);
    Set<CategoryCapability> capabilities = Set.of(
        CategoryCapability.READ_CATEGORY,
        CategoryCapability.ATTACH_CATEGORY,
        CategoryCapability.DETACH_CATEGORY,
        CategoryCapability.UPDATE_CATEGORY,
        CategoryCapability.CREATE_CHILD_CATEGORY,
        CategoryCapability.DELETE_CATEGORY);
    when(f.permissions.getCategoryAuthority(TYPED_ID)).thenReturn(authority);
    when(f.permissions.getCategoryCapabilities(TYPED_ID)).thenReturn(capabilities);

    CurrentUserCategoryPermissions result = f.update();

    assertSame(CategoryRole.EDITOR, result.getRole());
    assertFalse(result.isOwner());
    assertEquals(capabilities, result.getCapabilities());
    assertTrue(result.isCanEdit());
    assertFalse(result.isCanShare());
  }

  @Test
  void ownerIsReportedSeparatelyFromRole() {
    Fixture f = new Fixture();
    CategoryAuthority authority = new CategoryAuthority(null, true);
    when(f.permissions.getCategoryAuthority(TYPED_ID)).thenReturn(authority);
    when(f.permissions.getCategoryCapabilities(TYPED_ID)).thenReturn(authority.capabilities());

    CurrentUserCategoryPermissions result = f.update();

    assertTrue(result.isOwner());
    assertNull(result.getRole());
    assertTrue(result.isCanTransferOwnership());
  }

  @Test
  void updateHonorsTheOutputObjectPassedByTheCaller() {
    Fixture f = new Fixture();
    CurrentUserCategoryPermissions embedded = new CurrentUserCategoryPermissions();
    when(f.category.getCurrentUserPermissions()).thenReturn(embedded);
    CategoryAuthority authority = new CategoryAuthority(CategoryRole.MANAGER, false);
    when(f.permissions.getCategoryAuthority(TYPED_ID)).thenReturn(authority);
    when(f.permissions.getCategoryCapabilities(TYPED_ID)).thenReturn(authority.capabilities());

    CurrentUserCategoryPermissions output = f.update();

    assertTrue(output.isCanWrite());
    assertFalse(embedded.isCanWrite());
  }

  private static final class Fixture {
    private final CategoryPermissionServiceSession permissions = mock(CategoryPermissionServiceSession.class);
    private final CategoryWithCurrentUserPermissions category = mock(CategoryWithCurrentUserPermissions.class);

    private Fixture() {
      when(category.getId()).thenReturn(CATEGORY_ID);
    }

    private CurrentUserCategoryPermissions update() {
      CurrentUserCategoryPermissions output = new CurrentUserCategoryPermissions();
      CurrentUserPermissionUpdaterForGraphDbCategory.get(permissions, category).update(output);
      return output;
    }
  }
}
