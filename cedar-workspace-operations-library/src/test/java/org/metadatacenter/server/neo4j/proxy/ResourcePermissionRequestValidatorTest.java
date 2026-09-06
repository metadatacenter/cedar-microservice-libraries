package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.metadatacenter.server.security.model.permission.resource.*;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unit decision matrix for ACL request validation; no Neo4j driver is constructed. */
class ResourcePermissionRequestValidatorTest {

  private static final CedarFilesystemResourceId RESOURCE_ID =
      CedarFolderId.build("https://repo.example/folders/f1");
  private static final String OWNER_ID = "https://repo.example/users/owner";
  private static final String USER_ID = "https://repo.example/users/user";
  private static final String GROUP_ID = "https://repo.example/groups/group";
  private static final String EVERYONE_ID = "https://repo.example/groups/everyone";

  @Test
  void missingResourceStopsValidationAtExistence() {
    Fixture f = new Fixture();
    when(f.filesystemResources.findResourceById(RESOURCE_ID)).thenReturn(null);

    assertError(f.validate(f.validRequest()), CedarErrorKey.NODE_NOT_FOUND);

    verify(f.permissions, never()).userHasCapability(any(), any());
  }

  @Test
  void managerCapabilityIsRequiredToChangeGrants() {
    Fixture f = new Fixture();
    when(f.permissions.userHasCapability(RESOURCE_ID, ResourceCapability.MANAGE_GRANTS)).thenReturn(false);

    assertError(f.validate(f.validRequest()), CedarErrorKey.NOT_AUTHORIZED);
  }

  @Test
  void missingRequestBodyReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    assertError(f.validate(null), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void ownerMayBeOmittedAndTheCurrentOwnerIsPreserved() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(OWNER_ID, validator.getPermissions().getOwner().getId());
  }

  @Test
  void repeatingTheCurrentOwnerIsAcceptedForCompatibility() {
    Fixture f = new Fixture();
    assertTrue(f.validate(f.validRequest()).getCallResult().isOk());
  }

  @Test
  void aclUpdateCannotTransferOwnership() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setOwner(new ResourcePermissionUser(USER_ID));

    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void missingStoredOwnerIsInvalidData() {
    Fixture f = new Fixture();
    when(f.permissions.getResourcePermissions(RESOURCE_ID)).thenReturn(new CedarNodePermissionsWithExtract());
    assertError(f.validate(f.validRequest()), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void userEntryRequiresAUser() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(null, ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void userEntryRequiresARole() {
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
        new ResourcePermissionUser("https://repo.example/users/missing"), ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.USER_NOT_FOUND);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void userEntryRequiresANonBlankId(String userId) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(userId), ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryRequiresAGroup() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(null, ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryRequiresARole() {
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
        new ResourcePermissionGroup("https://repo.example/groups/missing"), ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.GROUP_NOT_FOUND);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void groupEntryRequiresANonBlankId(String groupId) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(groupId), ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void duplicateUsersAreRejectedEvenWhenTheirRolesDiffer() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setUserPermissions(List.of(
        new ResourcePermissionUserPermissionPair(new ResourcePermissionUser(USER_ID), ResourceRole.VIEWER),
        new ResourcePermissionUserPermissionPair(new ResourcePermissionUser(USER_ID), ResourceRole.EDITOR)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void duplicateGroupsAreRejectedEvenWhenTheirRolesDiffer() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setGroupPermissions(List.of(
        new ResourcePermissionGroupPermissionPair(new ResourcePermissionGroup(GROUP_ID), ResourceRole.VIEWER),
        new ResourcePermissionGroupPermissionPair(new ResourcePermissionGroup(GROUP_ID), ResourceRole.MANAGER)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void ownerCannotAlsoReceiveADirectRole() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(OWNER_ID), ResourceRole.VIEWER));
    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void nullPermissionCollectionsMeanNoDirectGrants() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.setUserPermissions(null);
    request.setGroupPermissions(null);

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertTrue(validator.getPermissions().getUserPermissions().isEmpty());
    assertTrue(validator.getPermissions().getGroupPermissions().isEmpty());
  }

  @Test
  void nullGrantEntriesAreRejected() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest users = f.validRequest();
    users.setUserPermissions(Collections.singletonList(null));
    assertError(f.validate(users), CedarErrorKey.MISSING_PARAMETER);

    ResourcePermissionsRequest groups = f.validRequest();
    groups.setGroupPermissions(Collections.singletonList(null));
    assertError(f.validate(groups), CedarErrorKey.MISSING_PARAMETER);
  }

  @ParameterizedTest
  @EnumSource(ResourceRole.class)
  void everyRoleCanBeGrantedDirectlyToAUser(ResourceRole role) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(USER_ID), role));

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(role, validator.getPermissions().getUserPermissions().get(0).getRole());
  }

  @ParameterizedTest
  @EnumSource(ResourceRole.class)
  void everyRoleCanBeGrantedDirectlyToAnOrdinaryGroup(ResourceRole role) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(GROUP_ID), role));

    ResourcePermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(role, validator.getPermissions().getGroupPermissions().get(0).getRole());
  }

  @Test
  void everyoneMayReceiveViewer() {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(EVERYONE_ID), ResourceRole.VIEWER));
    assertTrue(f.validate(request).getCallResult().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EDITOR", "MANAGER"})
  void everyoneCannotReceiveEditorOrManager(String roleName) {
    Fixture f = new Fixture();
    ResourcePermissionsRequest request = f.validRequest();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(EVERYONE_ID), ResourceRole.valueOf(roleName)));
    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  private static void assertError(ResourcePermissionRequestValidator validator, CedarErrorKey expected) {
    BackendCallResult<?> result = validator.getCallResult();
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
      when(permissions.userHasCapability(RESOURCE_ID, ResourceCapability.MANAGE_GRANTS)).thenReturn(true);
      when(permissions.getResourcePermissions(RESOURCE_ID)).thenReturn(currentPermissions());
      addUser(OWNER_ID);
      addUser(USER_ID);
      addGroup(GROUP_ID);
      FolderServerGroup everyone = addGroup(EVERYONE_ID);
      when(groups.getEverybodyGroup()).thenReturn(everyone);
    }

    private ResourcePermissionRequestValidator validate(ResourcePermissionsRequest request) {
      return new ResourcePermissionRequestValidator(permissions, proxies, RESOURCE_ID, request);
    }

    private ResourcePermissionsRequest validRequest() {
      ResourcePermissionsRequest request = new ResourcePermissionsRequest();
      request.setOwner(new ResourcePermissionUser(OWNER_ID));
      return request;
    }

    private void addUser(String id) {
      FolderServerUser user = new FolderServerUser();
      user.setId(id);
      when(users.findUserById(CedarUserId.build(id))).thenReturn(user);
    }

    private FolderServerGroup addGroup(String id) {
      FolderServerGroup group = new FolderServerGroup();
      group.setId(id);
      group.setName("group");
      when(groups.findGroupById(CedarGroupId.build(id))).thenReturn(group);
      return group;
    }

    private static CedarNodePermissionsWithExtract currentPermissions() {
      CedarNodePermissionsWithExtract current = new CedarNodePermissionsWithExtract();
      current.setOwner(new CedarUserExtract(OWNER_ID, null, null, null));
      return current;
    }
  }
}
