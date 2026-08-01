package org.metadatacenter.server.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Decision matrix for capabilities projected onto folder reports. */
class CurrentUserPermissionUpdaterForGraphDbFolderTest {

  private static final CedarFolderId FOLDER_ID = CedarFolderId.build("folder-1");

  @ParameterizedTest
  @CsvSource({
      "true,true,true,true,true",
      "true,false,true,true,true",
      "false,true,true,false,false",
      "false,false,false,false,false"
  })
  void accessProjectionDistinguishesWriteReadAndNoAccess(boolean write, boolean read,
      boolean canRead, boolean canWrite, boolean canDelete) {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToResource(FOLDER_ID)).thenReturn(write);
    when(f.permissions.userHasReadAccessToResource(FOLDER_ID)).thenReturn(read);

    CurrentUserResourcePermissions result = f.update();

    assertEquals(canRead, result.isCanRead());
    assertEquals(canWrite, result.isCanWrite());
    assertEquals(canDelete, result.isCanDelete());
  }

  @Test
  void writeAccessShortCircuitsReadLookup() {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToResource(FOLDER_ID)).thenReturn(true);

    f.update();

    verify(f.permissions, never()).userHasReadAccessToResource(FOLDER_ID);
  }

  @ParameterizedTest
  @CsvSource({
      "false,false,false,true",
      "true,false,false,false",
      "false,true,false,false",
      "false,false,true,false",
      "true,true,true,false"
  })
  void onlyOrdinaryFoldersWithWriteAccessAreShareable(boolean root, boolean system,
      boolean userHome, boolean canShare) {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToResource(FOLDER_ID)).thenReturn(true);
    when(f.folder.isRoot()).thenReturn(root);
    when(f.folder.isSystem()).thenReturn(system);
    when(f.folder.isUserHome()).thenReturn(userHome);

    assertEquals(canShare, f.update().isCanShare());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void writableFolderExposesExactlyTheOppositeOpenTransition(boolean open) {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToResource(FOLDER_ID)).thenReturn(true);
    when(f.folder.isOpen()).thenReturn(open);

    CurrentUserResourcePermissions result = f.update();

    assertEquals(!open, result.isCanMakeOpen());
    assertEquals(open, result.isCanMakeNotOpen());
  }

  @Test
  void readOnlyFolderDoesNotExposeOpenTransitions() {
    Fixture f = new Fixture();
    when(f.permissions.userHasReadAccessToResource(FOLDER_ID)).thenReturn(true);

    CurrentUserResourcePermissions result = f.update();

    assertFalse(result.isCanMakeOpen());
    assertFalse(result.isCanMakeNotOpen());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void ownerChangeCapabilityIsIndependent(boolean canChangeOwner) {
    Fixture f = new Fixture();
    when(f.permissions.userCanChangeOwnerOfResource(FOLDER_ID)).thenReturn(canChangeOwner);
    assertEquals(canChangeOwner, f.update().isCanChangeOwner());
  }

  @Test
  void updateHonorsTheOutputObjectPassedByTheCaller() {
    Fixture f = new Fixture();
    CurrentUserResourcePermissions embedded = new CurrentUserResourcePermissions();
    when(f.folder.getCurrentUserPermissions()).thenReturn(embedded);
    when(f.permissions.userHasWriteAccessToResource(FOLDER_ID)).thenReturn(true);

    CurrentUserResourcePermissions output = f.update();

    assertTrue(output.isCanWrite());
    assertFalse(embedded.isCanWrite(), "the updater must not silently redirect output to the folder");
  }

  private static final class Fixture {
    private final ResourcePermissionServiceSession permissions = mock(ResourcePermissionServiceSession.class);
    private final FolderWithCurrentUserPermissions folder = mock(FolderWithCurrentUserPermissions.class);

    private Fixture() {
      when(folder.getResourceId()).thenReturn(FOLDER_ID);
    }

    private CurrentUserResourcePermissions update() {
      CurrentUserResourcePermissions output = new CurrentUserResourcePermissions();
      CurrentUserPermissionUpdaterForGraphDbFolder.get(permissions, folder).update(output);
      return output;
    }
  }
}
