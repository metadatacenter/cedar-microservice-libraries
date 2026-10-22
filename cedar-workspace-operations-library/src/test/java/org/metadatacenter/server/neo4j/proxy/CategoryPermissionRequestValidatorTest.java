package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.*;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unit-level decision matrix for category ACL validation. */
class CategoryPermissionRequestValidatorTest {

  private static final CedarCategoryId CATEGORY_ID =
      CedarCategoryId.build("https://repo.example/categories/c1");
  private static final String OWNER_ID = "https://repo.example/users/owner";
  private static final String USER_ID = "https://repo.example/users/user";
  private static final String GROUP_ID = "https://repo.example/groups/group";
  private static final String EVERYONE_ID = "https://repo.example/groups/everyone";

  @Test
  void missingCategoryStopsValidationAtExistence() {
    Fixture f = new Fixture();
    when(f.categories.getCategoryById(CATEGORY_ID)).thenReturn(null);

    assertError(f.validate(f.validRequest()), CedarErrorKey.CATEGORY_NOT_FOUND);
    verifyNoInteractions(f.permissions);
  }

  @Test
  void manageGrantsCapabilityIsRequired() {
    Fixture f = new Fixture();
    when(f.permissions.userHasCapability(CATEGORY_ID, CategoryCapability.MANAGE_GRANTS)).thenReturn(false);

    assertError(f.validate(f.validRequest()), CedarErrorKey.NOT_AUTHORIZED);
  }

  @Test
  void missingRequestBodyReturnsStructuredError() {
    Fixture f = new Fixture();
    assertError(f.validate(null), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void ownerMayBeOmittedAndTheCurrentOwnerIsPreserved() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = new CategoryPermissionRequest();

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(OWNER_ID, validator.getPermissions().getOwner().getId());
  }

  @Test
  void ownershipCannotChangeThroughAclReplacement() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setOwner(new CategoryPermissionUser(USER_ID));

    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
    verify(f.users, never()).findUserById(CedarUserId.build(USER_ID));
  }

  @ParameterizedTest
  @EnumSource(CategoryRole.class)
  void everyRoleCanBeMaterializedForAUser(CategoryRole role) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(USER_ID), role));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertSame(role, validator.getPermissions().getUserPermissions().get(0).getRole());
  }

  @ParameterizedTest
  @EnumSource(CategoryRole.class)
  void everyRoleCanBeMaterializedForAGroup(CategoryRole role) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(GROUP_ID), role));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertSame(role, validator.getPermissions().getGroupPermissions().get(0).getRole());
  }

  @Test
  void duplicateUserRolesAreRejected() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setUserPermissions(List.of(
        new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(USER_ID), CategoryRole.VIEWER),
        new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(USER_ID), CategoryRole.EDITOR)));

    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void ownerCannotAlsoReceiveADirectRole() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(OWNER_ID), CategoryRole.VIEWER));

    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void everyoneMayReceiveViewer() {
    Fixture f = new Fixture();
    f.addGroup(EVERYONE_ID);
    when(f.groups.getEverybodyGroup()).thenReturn(f.group(EVERYONE_ID));
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(EVERYONE_ID), CategoryRole.VIEWER));

    assertTrue(f.validate(request).getCallResult().isOk());
  }

  @Test
  void rootAclReplacementCannotRemoveEveryoneViewer() {
    Fixture f = new Fixture();
    f.asRoot();
    f.addGroup(EVERYONE_ID);
    when(f.groups.getEverybodyGroup()).thenReturn(f.group(EVERYONE_ID));

    CategoryPermissionRequestValidator validator = f.validate(f.validRequest());

    assertTrue(validator.getCallResult().isOk());
    assertEquals(1, validator.getPermissions().getGroupPermissions().size());
    CategoryGroupPermission grant = validator.getPermissions().getGroupPermissions().get(0);
    assertEquals(EVERYONE_ID, grant.getGroup().getId());
    assertEquals(CategoryRole.VIEWER, grant.getRole());
  }

  @ParameterizedTest
  @EnumSource(value = CategoryRole.class, names = {"CLASSIFIER", "EDITOR", "MANAGER"})
  void everyoneCannotReceiveAWriteCapableRole(CategoryRole role) {
    Fixture f = new Fixture();
    f.addGroup(EVERYONE_ID);
    when(f.groups.getEverybodyGroup()).thenReturn(f.group(EVERYONE_ID));
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(EVERYONE_ID), role));

    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void aCategoryWithoutAStoredOwnerIsInvalidData() {
    Fixture f = new Fixture();
    when(f.permissions.getCategoryPermissions(CATEGORY_ID)).thenReturn(new CategoryPermissions());

    CategoryPermissionRequestValidator validator = f.validate(f.validRequest());

    assertError(validator, CedarErrorKey.INVALID_DATA);
    assertEquals(CedarErrorType.SERVER_ERROR, validator.getCallResult().getFirstError().getErrorPack().getErrorType());
  }

  @ParameterizedTest
  @MethodSource("malformedUserEntries")
  void aMalformedUserEntryIsRejectedByTheMissingPart(CategoryPermissionUserPermissionPair entry, String missing) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(entry);

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertMissingParameter(validator, missing);
    assertTrue(validator.getPermissions().getUserPermissions().isEmpty());
  }

  static Stream<Arguments> malformedUserEntries() {
    return Stream.of(
        Arguments.of(null, "userPermission"),
        Arguments.of(new CategoryPermissionUserPermissionPair(null, CategoryRole.VIEWER), "user"),
        Arguments.of(new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(null), CategoryRole.VIEWER),
            "userId"),
        Arguments.of(new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(" "), CategoryRole.VIEWER),
            "userId"),
        Arguments.of(new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(USER_ID), null), "role"));
  }

  @ParameterizedTest
  @MethodSource("malformedGroupEntries")
  void aMalformedGroupEntryIsRejectedByTheMissingPart(CategoryPermissionGroupPermissionPair entry, String missing) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(entry);

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertMissingParameter(validator, missing);
    assertTrue(validator.getPermissions().getGroupPermissions().isEmpty());
  }

  static Stream<Arguments> malformedGroupEntries() {
    return Stream.of(
        Arguments.of(null, "groupPermission"),
        Arguments.of(new CategoryPermissionGroupPermissionPair(null, CategoryRole.VIEWER), "group"),
        Arguments.of(new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(null), CategoryRole.VIEWER),
            "groupId"),
        Arguments.of(new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(""), CategoryRole.VIEWER),
            "groupId"),
        Arguments.of(new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(GROUP_ID), null), "role"));
  }

  @Test
  void aGrantToAnUnknownUserIsNotFound() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser("https://repo.example/users/missing"), CategoryRole.EDITOR));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertError(validator, CedarErrorKey.USER_NOT_FOUND);
    assertEquals(CedarErrorType.NOT_FOUND, validator.getCallResult().getFirstError().getErrorPack().getErrorType());
    assertTrue(validator.getPermissions().getUserPermissions().isEmpty());
  }

  @Test
  void aGrantToAnUnknownGroupIsNotFound() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup("https://repo.example/groups/missing"), CategoryRole.EDITOR));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertError(validator, CedarErrorKey.GROUP_NOT_FOUND);
    assertEquals(CedarErrorType.NOT_FOUND, validator.getCallResult().getFirstError().getErrorPack().getErrorType());
    assertTrue(validator.getPermissions().getGroupPermissions().isEmpty());
  }

  @Test
  void duplicateGroupRolesAreRejected() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setGroupPermissions(List.of(
        new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(GROUP_ID), CategoryRole.VIEWER),
        new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(GROUP_ID), CategoryRole.MANAGER)));

    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void rootAclReplacementKeepsAnExplicitEveryoneViewerOnce() {
    Fixture f = new Fixture();
    f.asRoot();
    f.addGroup(EVERYONE_ID);
    when(f.groups.getEverybodyGroup()).thenReturn(f.group(EVERYONE_ID));
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(EVERYONE_ID), CategoryRole.VIEWER));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(1, validator.getPermissions().getGroupPermissions().size());
    assertEquals(EVERYONE_ID, validator.getPermissions().getGroupPermissions().get(0).getGroup().getId());
  }

  @Test
  void rootGrantsCannotChangeWithoutTheEveryoneGroup() {
    Fixture f = new Fixture();
    f.asRoot();
    when(f.groups.getEverybodyGroup()).thenReturn(null);

    CategoryPermissionRequestValidator validator = f.validate(f.validRequest());

    assertError(validator, CedarErrorKey.INVALID_DATA);
    assertEquals(CedarErrorType.SERVER_ERROR, validator.getCallResult().getFirstError().getErrorPack().getErrorType());
  }

  private static void assertMissingParameter(CategoryPermissionRequestValidator validator, String paramName) {
    assertError(validator, CedarErrorKey.MISSING_PARAMETER);
    assertEquals(CedarErrorType.INVALID_ARGUMENT,
        validator.getCallResult().getFirstError().getErrorPack().getErrorType());
    assertEquals(paramName, validator.getCallResult().getFirstError().getErrorPack().getParameters().get("paramName"));
  }

  private static void assertError(CategoryPermissionRequestValidator validator, CedarErrorKey expected) {
    BackendCallResult result = validator.getCallResult();
    assertFalse(result.isOk());
    assertEquals(expected, result.getFirstError().getErrorPack().getErrorKey());
  }

  private static final class Fixture {
    private final CategoryPermissionServiceSession permissions = mock(CategoryPermissionServiceSession.class);
    private final Neo4JProxies proxies = mock(Neo4JProxies.class);
    private final Neo4JProxyCategory categories = mock(Neo4JProxyCategory.class);
    private final Neo4JProxyUser users = mock(Neo4JProxyUser.class);
    private final Neo4JProxyGroup groups = mock(Neo4JProxyGroup.class);
    private final FolderServerCategory category = new FolderServerCategory();

    private Fixture() {
      category.setId(CATEGORY_ID.getId());
      category.setParentCategoryId("https://repo.example/categories/root");
      when(proxies.category()).thenReturn(categories);
      when(proxies.user()).thenReturn(users);
      when(proxies.group()).thenReturn(groups);
      when(categories.getCategoryById(CATEGORY_ID)).thenReturn(category);
      when(permissions.userHasCapability(CATEGORY_ID, CategoryCapability.MANAGE_GRANTS)).thenReturn(true);
      when(permissions.getCategoryPermissions(CATEGORY_ID)).thenReturn(currentPermissions());
      addUser(USER_ID);
      addUser(OWNER_ID);
      addGroup(GROUP_ID);
    }

    private void asRoot() {
      category.setParentCategoryId(null);
    }

    private CategoryPermissionRequestValidator validate(CategoryPermissionRequest request) {
      return new CategoryPermissionRequestValidator(permissions, proxies, CATEGORY_ID, request);
    }

    private CategoryPermissionRequest validRequest() {
      CategoryPermissionRequest request = new CategoryPermissionRequest();
      request.setOwner(new CategoryPermissionUser(OWNER_ID));
      return request;
    }

    private void addUser(String id) {
      FolderServerUser user = new FolderServerUser();
      user.setId(id);
      when(users.findUserById(CedarUserId.build(id))).thenReturn(user);
    }

    private void addGroup(String id) {
      when(groups.findGroupById(CedarGroupId.build(id))).thenReturn(group(id));
    }

    private FolderServerGroup group(String id) {
      FolderServerGroup group = new FolderServerGroup();
      group.setId(id);
      group.setName("group");
      return group;
    }

    private CategoryPermissions currentPermissions() {
      CategoryPermissions current = new CategoryPermissions();
      current.setOwner(new CedarUserExtract(OWNER_ID, null, null, null));
      return current;
    }
  }
}
