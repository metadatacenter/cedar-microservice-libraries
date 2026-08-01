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
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.*;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Unit-level decision matrix for category ACL validation; no Neo4j driver is constructed. */
class CategoryPermissionRequestValidatorTest {

  private static final CedarCategoryId CATEGORY_ID =
      CedarCategoryId.build("https://repo.example/categories/c1");
  private static final String OWNER_ID = "https://repo.example/users/owner";
  private static final String USER_ID = "https://repo.example/users/user";
  private static final String GROUP_ID = "https://repo.example/groups/group";

  @Test
  void missingCategoryStopsValidationAtExistence() {
    Fixture f = new Fixture();
    when(f.categories.getCategoryById(CATEGORY_ID)).thenReturn(null);

    CategoryPermissionRequestValidator validator = f.validate(f.validRequest());

    assertError(validator, CedarErrorKey.CATEGORY_NOT_FOUND);
    verifyNoInteractions(f.permissions);
    verifyNoInteractions(f.users);
    verifyNoInteractions(f.groups);
  }

  @Test
  void missingWriteAccessStopsBeforeRequestContentsAreRead() {
    Fixture f = new Fixture();
    when(f.permissions.userHasWriteAccessToCategory(CATEGORY_ID)).thenReturn(false);

    CategoryPermissionRequestValidator validator = f.validate(new CategoryPermissionRequest());

    assertError(validator, CedarErrorKey.NO_WRITE_ACCESS_TO_CATEGORY);
    verifyNoInteractions(f.users);
    verifyNoInteractions(f.groups);
  }

  @Test
  void ownerIsRequired() {
    Fixture f = new Fixture();
    assertError(f.validate(new CategoryPermissionRequest()), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void missingRequestBodyReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    assertError(f.validate(null), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void ownerMustResolveToAKnownUser() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setOwner(new CategoryPermissionUser("https://repo.example/users/missing"));
    assertError(f.validate(request), CedarErrorKey.USER_NOT_FOUND);
  }

  @Test
  void userEntryRequiresAUser() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(null, CategoryPermission.ATTACH));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void userEntryRequiresAPermission() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(USER_ID), null));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void userEntryMustResolveToAKnownUser() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser("https://repo.example/users/missing"), CategoryPermission.ATTACH));
    assertError(f.validate(request), CedarErrorKey.USER_NOT_FOUND);
  }

  @Test
  void groupEntryRequiresAGroup() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(null, CategoryPermission.ATTACH));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryRequiresAPermission() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(GROUP_ID), null));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void groupEntryMustResolveToAKnownGroup() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup("https://repo.example/groups/missing"), CategoryPermission.ATTACH));
    assertError(f.validate(request), CedarErrorKey.GROUP_NOT_FOUND);
  }

  @Test
  void duplicateUsersAreRejectedEvenWhenTheirPermissionsDiffer() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setUserPermissions(List.of(
        new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(USER_ID), CategoryPermission.ATTACH),
        new CategoryPermissionUserPermissionPair(new CategoryPermissionUser(USER_ID), CategoryPermission.WRITE)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void duplicateGroupsAreRejectedEvenWhenTheirPermissionsDiffer() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setGroupPermissions(List.of(
        new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(GROUP_ID), CategoryPermission.ATTACH),
        new CategoryPermissionGroupPermissionPair(new CategoryPermissionGroup(GROUP_ID), CategoryPermission.WRITE)));
    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  @Test
  void ownerCannotAlsoAppearAsAUserGrantee() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(OWNER_ID), CategoryPermission.ATTACH));
    assertError(f.validate(request), CedarErrorKey.INVALID_DATA);
  }

  @Test
  void explicitNullPermissionCollectionsAreTreatedAsNoAdditionalGrants() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setUserPermissions(null);
    request.setGroupPermissions(null);

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertTrue(validator.getPermissions().getUserPermissions().isEmpty());
    assertTrue(validator.getPermissions().getGroupPermissions().isEmpty());
  }

  @Test
  void nullUserPermissionEntryProducesAValidationError() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setUserPermissions(Collections.singletonList(null));

    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void nullGroupPermissionEntryProducesAValidationError() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.setGroupPermissions(Collections.singletonList(null));

    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void retainingTheCurrentOwnerNeedsNoOwnerTransferAuthority() {
    Fixture f = new Fixture();
    CategoryPermissionRequestValidator validator = f.validate(f.validRequest());

    assertTrue(validator.getCallResult().isOk());
    verify(f.permissions, never()).userHas(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY);
    verify(f.permissions, never()).userIsOwnerOfCategory(CATEGORY_ID);
  }

  @Test
  void privilegedCallerCanTransferOwnership() {
    Fixture f = new Fixture();
    when(f.permissions.userHas(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY)).thenReturn(true);

    CategoryPermissionRequestValidator validator = f.validate(f.requestOwnedBy(USER_ID));

    assertTrue(validator.getCallResult().isOk());
    verify(f.permissions, never()).userIsOwnerOfCategory(CATEGORY_ID);
  }

  @Test
  void currentOwnerCanTransferOwnership() {
    Fixture f = new Fixture();
    when(f.permissions.userIsOwnerOfCategory(CATEGORY_ID)).thenReturn(true);
    assertTrue(f.validate(f.requestOwnedBy(USER_ID)).getCallResult().isOk());
  }

  @Test
  void nonOwnerWithoutOverrideCannotTransferOwnership() {
    Fixture f = new Fixture();
    assertError(f.validate(f.requestOwnedBy(USER_ID)), CedarErrorKey.NOT_AUTHORIZED);
  }

  @ParameterizedTest
  @EnumSource(CategoryPermission.class)
  void everyDeclaredPermissionCanBeMaterializedForAUser(CategoryPermission permission) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(USER_ID), permission));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(permission, validator.getPermissions().getUserPermissions().get(0).getPermission());
  }

  @ParameterizedTest
  @EnumSource(CategoryPermission.class)
  void everyDeclaredPermissionCanBeMaterializedForAGroup(CategoryPermission permission) {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(GROUP_ID), permission));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(permission, validator.getPermissions().getGroupPermissions().get(0).getPermission());
  }

  @Test
  void validMixedRequestBuildsCanonicalResolvedPermissions() {
    Fixture f = new Fixture();
    CategoryPermissionRequest request = f.validRequest();
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(USER_ID), CategoryPermission.WRITE));
    request.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(GROUP_ID), CategoryPermission.ATTACH));

    CategoryPermissionRequestValidator validator = f.validate(request);

    assertTrue(validator.getCallResult().isOk());
    assertEquals(OWNER_ID, validator.getPermissions().getOwner().getId());
    assertEquals(USER_ID, validator.getPermissions().getUserPermissions().get(0).getUser().getId());
    assertEquals(GROUP_ID, validator.getPermissions().getGroupPermissions().get(0).getGroup().getId());
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

    private Fixture() {
      FolderServerCategory category = new FolderServerCategory();
      category.setId(CATEGORY_ID.getId());
      when(proxies.category()).thenReturn(categories);
      when(proxies.user()).thenReturn(users);
      when(proxies.group()).thenReturn(groups);
      when(categories.getCategoryById(CATEGORY_ID)).thenReturn(category);
      when(permissions.userHasWriteAccessToCategory(CATEGORY_ID)).thenReturn(true);
      when(permissions.getCategoryPermissions(CATEGORY_ID)).thenReturn(currentPermissions(OWNER_ID));
      addUser(OWNER_ID);
      addUser(USER_ID);
      addGroup(GROUP_ID);
    }

    private CategoryPermissionRequestValidator validate(CategoryPermissionRequest request) {
      return new CategoryPermissionRequestValidator(permissions, proxies, CATEGORY_ID, request);
    }

    private CategoryPermissionRequest validRequest() {
      return requestOwnedBy(OWNER_ID);
    }

    private CategoryPermissionRequest requestOwnedBy(String ownerId) {
      CategoryPermissionRequest request = new CategoryPermissionRequest();
      request.setOwner(new CategoryPermissionUser(ownerId));
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

    private static CategoryPermissions currentPermissions(String ownerId) {
      CategoryPermissions current = new CategoryPermissions();
      current.setOwner(new CedarUserExtract(ownerId, null, null, null));
      return current;
    }
  }
}
