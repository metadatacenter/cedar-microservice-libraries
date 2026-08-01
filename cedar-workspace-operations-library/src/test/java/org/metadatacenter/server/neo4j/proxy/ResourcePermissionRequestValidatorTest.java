package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsWithExtract;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.resource.*;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unit-level decision matrix for ACL request validation; no Neo4j driver is constructed. */
class ResourcePermissionRequestValidatorTest {

  private static final CedarFilesystemResourceId RESOURCE_ID =
      CedarFolderId.build("https://repo.example/folders/f1");
  private static final String OWNER_ID = "https://repo.example/users/owner";
  private static final String USER_ID = "https://repo.example/users/user";
  private static final String GROUP_ID = "https://repo.example/groups/group";

  @Test
  void missingResourceStopsValidationAtExistence() {
    Fixture f = new Fixture();
    when(f.filesystemResources.findResourceById(RESOURCE_ID)).thenReturn(null);

    ResourcePermissionRequestValidator validator = f.validate(f.validRequest());

    assertError(validator, CedarErrorKey.NODE_NOT_FOUND);
    verifyNoInteractions(f.permissions);
    verifyNoInteractions(f.users);
    verifyNoInteractions(f.groups);
  }

  @Test
  void missingWriteAccessStopsBeforeRequestContentsAreRead() {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToResource(RESOURCE_ID)).thenReturn(false);

    ResourcePermissionRequestValidator validator = f.validate(new ResourcePermissionsRequest());

    assertError(validator, CedarErrorKey.NO_WRITE_ACCESS_TO_RESOURCE);
    verifyNoInteractions(f.users);
    verifyNoInteractions(f.groups);
  }

  @Test
  void ownerIsRequired() {
    Fixture f = new Fixture();
    assertError(f.validate(new ResourcePermissionsRequest()), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void ownerMustResolveToAKnownUser() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setOwner(new ResourcePermissionUser("https://repo.example/users/missing"));
    assertError(f.validate(request), CedarErrorKey.USER_NOT_FOUND);
  }

  @Test
  void userEntryRequiresAUser() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(null,
        FilesystemResourcePermission.READ));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void userEntryRequiresAPermission() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(USER_ID), null));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void userEntryMustResolveToAKnownUser() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser("https://repo.example/users/missing"), FilesystemResourcePermission.READ));
    assertError(f.validate(request), CedarErrorKey.USER_NOT_FOUND);
  }

  @Test
  void groupEntryRequiresAGroup() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(null,
        FilesystemResourcePermission.READ));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryRequiresAPermission() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(GROUP_ID), null));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryMustResolveToAKnownGroup() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup("https://repo.example/groups/missing"), FilesystemResourcePermission.READ));
    assertError(f.validate(request), CedarErrorKey.GROUP_NOT_FOUND);
  }

  @Test
  void duplicateUsersAreRejectedEvenWhenTheirPermissionsDiffer() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setUserPermissions(List.of(
        new ResourcePermissionUserPermissionPair(new ResourcePermissionUser(USER_ID),
            FilesystemResourcePermission.READ),
        new ResourcePermissionUserPermissionPair(new ResourcePermissionUser(USER_ID),
            FilesystemResourcePermission.WRITE)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void duplicateGroupsAreRejectedEvenWhenTheirPermissionsDiffer() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setGroupPermissions(List.of(
        new ResourcePermissionGroupPermissionPair(new ResourcePermissionGroup(GROUP_ID),
            FilesystemResourcePermission.READ),
        new ResourcePermissionGroupPermissionPair(new ResourcePermissionGroup(GROUP_ID),
            FilesystemResourcePermission.WRITE)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void ownerCannotAlsoAppearAsAUserGrantee() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(OWNER_ID), FilesystemResourcePermission.READ));
    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void retainingTheCurrentOwnerNeedsNoOwnerTransferAuthority() {
    Fixture f = new Fixture();
    ResourcePermissionRequestValidator validator = f.validate(f.validRequest());

    assertTrue(validator.getCallResult().isOk());
    verify(f.permissions, never()).userHasPermission(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE);
    verify(f.permissions, never()).userIsOwnerOfResource(RESOURCE_ID);
  }

  @Test
  void privilegedCallerCanTransferOwnership() {
    Fixture f = new Fixture();
    when(f.permissions.userHasPermission(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE)).thenReturn(true);

    ResourcePermissionRequestValidator validator = f.validate(f.requestOwnedBy(USER_ID));

    assertTrue(validator.getCallResult().isOk());
    verify(f.permissions, never()).userIsOwnerOfResource(RESOURCE_ID);
  }

  @Test
  void currentOwnerCanTransferOwnership() {
    Fixture f = new Fixture();
    when(f.permissions.userIsOwnerOfResource(RESOURCE_ID)).thenReturn(true);
    assertTrue(f.validate(f.requestOwnedBy(USER_ID)).getCallResult().isOk());
  }

  @Test
  void nonOwnerWithoutOverrideCannotTransferOwnership() {
    Fixture f = new Fixture();
    assertError(f.validate(f.requestOwnedBy(USER_ID)), CedarErrorKey.NOT_AUTHORIZED);
  }

  @ParameterizedTest
  @EnumSource(FilesystemResourcePermission.class)
  void everyDeclaredPermissionCanBeMaterializedForAUser(FilesystemResourcePermission permission) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(USER_ID), permission));

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(permission, validator.getPermissions().getUserPermissions().get(0).getPermission());
  }

  @ParameterizedTest
  @EnumSource(FilesystemResourcePermission.class)
  void everyDeclaredPermissionCanBeMaterializedForAGroup(FilesystemResourcePermission permission) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(GROUP_ID), permission));

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(permission, validator.getPermissions().getGroupPermissions().get(0).getPermission());
  }

  @Test
  void validMixedRequestBuildsCanonicalResolvedPermissions() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(USER_ID), FilesystemResourcePermission.WRITE));
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(GROUP_ID), FilesystemResourcePermission.READ));

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(OWNER_ID, validator.getPermissions().getOwner().getId());
    assertEquals(USER_ID, validator.getPermissions().getUserPermissions().get(0).getUser().getId());
    assertEquals(GROUP_ID, validator.getPermissions().getGroupPermissions().get(0).getGroup().getId());
  }

  private static void assertError(ResourcePermissionRequestValidator validator, CedarErrorKey expected) {
    BackendCallResult result = validator.getCallResult();
    assertFalse(result.isOk());
    assertEquals(expected, result.getFirstError().getErrorPack().getErrorKey());
  }

  private static final class Fixture {
    private final ResourcePermissionServiceSession permissions = mock(ResourcePermissionServiceSession.class);
    private final Neo4JProxies proxies = mock(Neo4JProxies.class);
    private final Neo4JProxyFilesystemResource filesystemResources = mock(Neo4JProxyFilesystemResource.class);
    private final Neo4JProxyUser users = mock(Neo4JProxyUser.class);
    private final Neo4JProxyGroup groups = mock(Neo4JProxyGroup.class);

    private Fixture() {
      FileSystemResource resource = mock(FileSystemResource.class);
      when(resource.getResourceId()).thenReturn(RESOURCE_ID);
      when(proxies.filesystemResource()).thenReturn(filesystemResources);
      when(proxies.user()).thenReturn(users);
      when(proxies.group()).thenReturn(groups);
      when(filesystemResources.findResourceById(RESOURCE_ID)).thenReturn(resource);
      when(permissions.userHasWriteAccessToResource(RESOURCE_ID)).thenReturn(true);
      when(permissions.getResourcePermissions(RESOURCE_ID)).thenReturn(currentPermissions(OWNER_ID));
      addUser(OWNER_ID);
      addUser(USER_ID);
      addGroup(GROUP_ID);
    }

    private ResourcePermissionRequestValidator validate(ResourcePermissionsRequest request) {
      return new ResourcePermissionRequestValidator(permissions, proxies, RESOURCE_ID, request);
    }

    private ResourcePermissionsRequest validRequest() {
      return requestOwnedBy(OWNER_ID);
    }

    private ResourcePermissionsRequest requestOwnedBy(String ownerId) {
      ResourcePermissionsRequest request = new ResourcePermissionsRequest();
      request.setOwner(new ResourcePermissionUser(ownerId));
      return request;
    }

    private void addUser(String id) {
      FolderServerUser user = new FolderServerUser();
      user.setId(id);
      when(users.findUserById(CedarUserId.build(id))).thenReturn(user);
    }

    private void addGroup(String id) {
      FolderServerGroup group = new FolderServerGroup();
      group.setId(id);
      group.setName("group");
      when(groups.findGroupById(CedarGroupId.build(id))).thenReturn(group);
    }

    private static CedarNodePermissionsWithExtract currentPermissions(String ownerId) {
      CedarNodePermissionsWithExtract current = new CedarNodePermissionsWithExtract();
      current.setOwner(new CedarUserExtract(ownerId, null, null, null));
      return current;
    }
  }
}
