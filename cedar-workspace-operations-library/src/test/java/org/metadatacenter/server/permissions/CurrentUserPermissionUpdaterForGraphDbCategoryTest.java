package org.metadatacenter.server.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryWithCurrentUserPermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Decision matrix for capabilities projected onto category reports. */
class CurrentUserPermissionUpdaterForGraphDbCategoryTest {

  private static final String CATEGORY_ID = "category-1";
  private static final CedarCategoryId TYPED_ID = CedarCategoryId.build(CATEGORY_ID);

  @ParameterizedTest
  @CsvSource({
      "true,true,true,true,true,true",
      "true,false,true,true,true,true",
      "false,true,false,false,true,true",
      "false,false,false,false,false,false"
  })
  void accessProjectionDistinguishesWriteAttachAndNoGrant(boolean write, boolean attach,
      boolean canWrite, boolean canDelete, boolean canAttach, boolean canDetach) {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToCategory(TYPED_ID)).thenReturn(write);
    when(f.permissions.userHasAttachAccessToCategory(TYPED_ID)).thenReturn(attach);

    CurrentUserCategoryPermissions result = f.update();

    assertTrue(result.isCanRead(), "categories are globally readable");
    assertEquals(canWrite, result.isCanWrite());
    assertEquals(canDelete, result.isCanDelete());
    assertEquals(canAttach, result.isCanAttach());
    assertEquals(canDetach, result.isCanDetach());
  }

  @Test
  void writeAccessShortCircuitsAttachLookup() {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToCategory(TYPED_ID)).thenReturn(true);

    f.update();

    verify(f.permissions, never()).userHasAttachAccessToCategory(TYPED_ID);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void writableRootCannotBeSharedButOrdinaryCategoryCan(boolean root) {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToCategory(TYPED_ID)).thenReturn(true);
    when(f.category.isRoot()).thenReturn(root);

    assertEquals(!root, f.update().isCanShare());
  }

  @Test
  void attachOnlyDoesNotConferSharing() {
    Fixture f = new Fixture();
    when(f.permissions.userHasAttachAccessToCategory(TYPED_ID)).thenReturn(true);
    assertFalse(f.update().isCanShare());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void ownerChangeCapabilityIsIndependent(boolean canChangeOwner) {
    Fixture f = new Fixture();
    when(f.permissions.userCanChangeOwnerOfCategory(TYPED_ID)).thenReturn(canChangeOwner);
    assertEquals(canChangeOwner, f.update().isCanChangeOwner());
  }

  @Test
  void updateHonorsTheOutputObjectPassedByTheCaller() {
    Fixture f = new Fixture();
    CurrentUserCategoryPermissions embedded = new CurrentUserCategoryPermissions();
    when(f.category.getCurrentUserPermissions()).thenReturn(embedded);
    when(f.permissions.userHasWriteAccessToCategory(TYPED_ID)).thenReturn(true);

    CurrentUserCategoryPermissions output = f.update();

    assertTrue(output.isCanWrite());
    assertFalse(embedded.isCanWrite(), "the updater must not silently redirect output to the category");
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
