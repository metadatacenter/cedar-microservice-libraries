package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.metadatacenter.error.CedarErrorKey;
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
