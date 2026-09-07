package org.metadatacenter.server.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAction;
import org.metadatacenter.server.security.model.permission.resource.ResourceAccessContext;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapabilityPolicy;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.model.CedarResourceType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Matrix for projecting folder authority into capabilities and context-dependent actions. */
class CurrentUserPermissionUpdaterForGraphDbFolderTest {

  private static final CedarFolderId FOLDER_ID = CedarFolderId.build("folder-1");

  @ParameterizedTest
  @MethodSource("authorities")
  void projectsEveryRoleAndOwnershipCombination(ResourceAuthority authority,
      ResourceRole effectiveRole, boolean canRead, boolean canEdit, boolean canManage,
      boolean canTransferOwnership) {
    Fixture f = new Fixture(authority);

    CurrentUserResourcePermissions result = f.update();

    assertEquals(effectiveRole, result.getCurrentUserRole());
    assertEquals(effectiveRole, result.getRole());
    assertEquals(authority.owner(), result.isOwner());
    assertEquals(canRead, result.isCanRead());
    assertEquals(canEdit, result.isCanEdit());
    assertEquals(canEdit, result.isCanCreate());
    assertEquals(canManage, result.isCanWrite());
    assertEquals(canEdit, result.isCanDelete());
    assertFalse(result.isCanCopy(), "folders are not copy sources");
    assertEquals(canManage, result.isCanManageGrants());
    assertEquals(canManage, result.isCanMove());
    assertEquals(canManage, result.isCanManageOpenView());
    assertEquals(canTransferOwnership, result.isCanTransferOwnership());
    assertEquals(canRead,
        result.getCapabilities().contains(ResourceCapability.LIST_FOLDER_CONTENTS));
    assertEquals(canEdit,
        result.getCapabilities().contains(ResourceCapability.CREATE_IN_FOLDER));
    assertEquals(canEdit,
        result.getCapabilities().contains(ResourceCapability.COPY_INTO_FOLDER));
    assertEquals(canEdit,
        result.getCapabilities().contains(ResourceCapability.MOVE_INTO_FOLDER));
    assertEquals(folderCapabilities(canRead, canEdit, canManage, canTransferOwnership),
        result.getCapabilities());
    assertEquals(canManage ? Set.of(ResourceAction.ENABLE_OPENVIEW) : Set.of(),
        result.getAvailableActions());
  }

  private static Stream<Arguments> authorities() {
    return Stream.of(
        Arguments.of(new ResourceAuthority(null, false), null, false, false, false, false),
        Arguments.of(new ResourceAuthority(ResourceRole.VIEWER, false), ResourceRole.VIEWER,
            true, false, false, false),
        Arguments.of(new ResourceAuthority(ResourceRole.EDITOR, false), ResourceRole.EDITOR,
            true, true, false, false),
        Arguments.of(new ResourceAuthority(ResourceRole.MANAGER, false), ResourceRole.MANAGER,
            true, true, true, false),
        Arguments.of(new ResourceAuthority(null, true), null,
            true, true, true, true));
  }

  private static Set<ResourceCapability> folderCapabilities(
      boolean canRead, boolean canEdit, boolean canManage, boolean canTransferOwnership) {
    Set<ResourceCapability> capabilities = new LinkedHashSet<>();
    if (canRead) {
      capabilities.add(ResourceCapability.READ_RESOURCE);
      capabilities.add(ResourceCapability.LIST_FOLDER_CONTENTS);
    }
    if (canEdit) {
      capabilities.add(ResourceCapability.UPDATE_RESOURCE);
      capabilities.add(ResourceCapability.CREATE_IN_FOLDER);
      capabilities.add(ResourceCapability.COPY_INTO_FOLDER);
      capabilities.add(ResourceCapability.MOVE_INTO_FOLDER);
      capabilities.add(ResourceCapability.DELETE_RESOURCE);
    }
    if (canManage) {
      capabilities.add(ResourceCapability.MANAGE_GRANTS);
      capabilities.add(ResourceCapability.MOVE_RESOURCE);
      capabilities.add(ResourceCapability.MANAGE_OPENVIEW);
    }
    if (canTransferOwnership) {
      capabilities.add(ResourceCapability.TRANSFER_OWNERSHIP);
    }
    return capabilities;
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void managerExposesExactlyTheOppositeOpenViewTransition(boolean open) {
    Fixture f = new Fixture(new ResourceAuthority(ResourceRole.MANAGER, false));
    when(f.folder.isOpen()).thenReturn(open);

    CurrentUserResourcePermissions result = f.update();

    assertEquals(!open, result.isCanMakeOpen());
    assertEquals(open, result.isCanMakeNotOpen());
  }

  @Test
  void editorCannotManageOpenView() {
    Fixture f = new Fixture(new ResourceAuthority(ResourceRole.EDITOR, false));
    assertFalse(f.update().isCanMakeOpen());
  }

  @Test
  void rootSystemAndHomeFoldersCannotPerformProtectedOperations() {
    for (String specialKind : new String[]{"root", "system", "home"}) {
      Fixture f = new Fixture(new ResourceAuthority(ResourceRole.MANAGER, false));
      when(f.folder.isRoot()).thenReturn("root".equals(specialKind));
      when(f.folder.isSystem()).thenReturn("system".equals(specialKind));
      when(f.folder.isUserHome()).thenReturn("home".equals(specialKind));

      CurrentUserResourcePermissions result = f.update();
      assertFalse(result.isCanManageGrants(), specialKind);
      assertFalse(result.isCanManageOpenView(), specialKind);
      assertFalse(result.isCanMakeOpen(), specialKind);
      assertFalse(result.isCanDelete(), specialKind);
      assertFalse(result.isCanMove(), specialKind);
      assertFalse(result.isCanTransferOwnership(), specialKind);
    }
  }

  @Test
  void updateHonorsTheOutputObjectPassedByTheCaller() {
    Fixture f = new Fixture(new ResourceAuthority(ResourceRole.EDITOR, false));
    CurrentUserResourcePermissions embedded = new CurrentUserResourcePermissions();
    when(f.folder.getCurrentUserPermissions()).thenReturn(embedded);

    CurrentUserResourcePermissions output = f.update();

    assertTrue(output.isCanEdit());
    assertFalse(embedded.isCanEdit(), "the updater must not silently redirect output to the folder");
  }

  private static final class Fixture {
    private final ResourcePermissionServiceSession permissions = mock(ResourcePermissionServiceSession.class);
    private final FolderWithCurrentUserPermissions folder = mock(FolderWithCurrentUserPermissions.class);
    private final ResourceAuthority authority;

    private Fixture(ResourceAuthority authority) {
      this.authority = authority;
      when(folder.getResourceId()).thenReturn(FOLDER_ID);
      when(permissions.getResourceAuthority(FOLDER_ID)).thenReturn(authority);
    }

    private CurrentUserResourcePermissions update() {
      boolean protectedFolder = folder.isRoot() || folder.isSystem() || folder.isUserHome();
      when(permissions.getResourceCapabilities(FOLDER_ID)).thenReturn(
          ResourceCapabilityPolicy.evaluate(authority,
              new ResourceAccessContext(CedarResourceType.FOLDER, protectedFolder), new CedarUser()));
      CurrentUserResourcePermissions output = new CurrentUserResourcePermissions();
      CurrentUserPermissionUpdaterForGraphDbFolder.get(permissions, folder).update(output);
      return output;
    }
  }
}
