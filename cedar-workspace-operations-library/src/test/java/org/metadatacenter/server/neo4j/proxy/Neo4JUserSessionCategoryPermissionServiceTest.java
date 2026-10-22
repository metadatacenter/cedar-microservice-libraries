package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionRequest;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The decisions the category permission session makes before the graph is written: who may transfer a
 * category, to whom, and what is reported when a validated change does not land. No Neo4j driver is
 * constructed.
 */
class Neo4JUserSessionCategoryPermissionServiceTest {

  private static final CedarCategoryId CATEGORY_ID = CedarCategoryId.build("https://repo.example/categories/c1");
  private static final String CURRENT_USER_ID = "https://repo.example/users/current";
  private static final String OTHER_USER_ID = "https://repo.example/users/other";
  private static final RevisionPrecondition PRECONDITION = RevisionPrecondition.exact(4);

  private Neo4JProxyCategory categories;
  private Neo4JProxyUser users;
  private Neo4JProxyCategoryPermission categoryPermissions;
  private FolderServerCategory category;
  private CategoryPermissionServiceSession service;

  @BeforeEach
  void setUp() {
    CedarConfig config = mock(CedarConfig.class);
    when(config.getLinkedDataUtil()).thenReturn(mock(LinkedDataUtil.class));
    Neo4JProxies proxies = mock(Neo4JProxies.class);
    categories = mock(Neo4JProxyCategory.class);
    users = mock(Neo4JProxyUser.class);
    categoryPermissions = mock(Neo4JProxyCategoryPermission.class);
    when(proxies.category()).thenReturn(categories);
    when(proxies.user()).thenReturn(users);
    when(proxies.group()).thenReturn(mock(Neo4JProxyGroup.class));
    when(proxies.categoryPermission()).thenReturn(categoryPermissions);

    category = new FolderServerCategory();
    category.setId(CATEGORY_ID.getId());
    category.setParentCategoryId("https://repo.example/categories/root");
    when(categories.getCategoryById(CATEGORY_ID)).thenReturn(category);
    when(categories.getCategoryOwner(CATEGORY_ID)).thenReturn(user(CURRENT_USER_ID));
    when(users.findUserById(CedarUserId.build(OTHER_USER_ID))).thenReturn(user(OTHER_USER_ID));

    CedarUser currentUser = new CedarUser();
    currentUser.setId(CURRENT_USER_ID);
    service = Neo4JUserSessionCategoryPermissionService.get(config, proxies, currentUser, "global", "local");
  }

  @Test
  void theOwnerTransfersToAnotherUserUnderTheCallersPrecondition() {
    VersionedCategoryPermissions transferred = new VersionedCategoryPermissions(new CategoryPermissions(), 5);
    when(categoryPermissions.transferOwnership(CATEGORY_ID, CedarUserId.build(CURRENT_USER_ID),
        CedarUserId.build(OTHER_USER_ID), PRECONDITION)).thenReturn(transferred);

    BackendCallResult<VersionedCategoryPermissions> result = transferToOther();

    assertTrue(result.isOk());
    assertSame(transferred, result.getPayload());
  }

  @Test
  void anUnknownCategoryIsNotFound() {
    when(categories.getCategoryById(CATEGORY_ID)).thenReturn(null);

    assertRefused(transferToOther(), CedarErrorType.NOT_FOUND, CedarErrorKey.CATEGORY_NOT_FOUND);
  }

  @Test
  void theRootCategoryCannotBeTransferredEvenByItsOwner() {
    category.setParentCategoryId(null);

    assertRefused(transferToOther(), CedarErrorType.INVALID_ARGUMENT, CedarErrorKey.INVALID_INPUT);
  }

  @Test
  void onlyTheOwnerMayTransfer() {
    when(categories.getCategoryOwner(CATEGORY_ID)).thenReturn(user(OTHER_USER_ID));

    assertRefused(transferToOther(), CedarErrorType.PERMISSION, CedarErrorKey.NOT_AUTHORIZED);
  }

  @Test
  void aCategoryWithNoRecordedOwnerCannotBeTransferred() {
    when(categories.getCategoryOwner(CATEGORY_ID)).thenReturn(null);

    assertRefused(transferToOther(), CedarErrorType.PERMISSION, CedarErrorKey.NOT_AUTHORIZED);
  }

  @Test
  void theNewOwnerMustExist() {
    when(users.findUserById(CedarUserId.build(OTHER_USER_ID))).thenReturn(null);

    assertRefused(transferToOther(), CedarErrorType.NOT_FOUND, CedarErrorKey.USER_NOT_FOUND);
  }

  @Test
  void theOwnerCannotTransferToThemselves() {
    when(users.findUserById(CedarUserId.build(CURRENT_USER_ID))).thenReturn(user(CURRENT_USER_ID));

    BackendCallResult<VersionedCategoryPermissions> result = service.transferCategoryOwnership(CATEGORY_ID,
        CedarUserId.build(CURRENT_USER_ID), PRECONDITION);

    assertRefused(result, CedarErrorType.INVALID_ARGUMENT, CedarErrorKey.INVALID_INPUT);
  }

  @Test
  void aTransferWhoseCategoryVanishedBeforeTheWriteIsAServerError() {
    when(categoryPermissions.transferOwnership(any(), any(), any(), any())).thenReturn(null);

    BackendCallResult<VersionedCategoryPermissions> result = transferToOther();

    assertTrue(result.isError());
    assertEquals(CedarErrorType.SERVER_ERROR, result.getFirstError().getErrorPack().getErrorType());
  }

  @Test
  void anAclTheValidatorRefusesIsNeverWritten() {
    when(categoryPermissions.getCategoryAuthority(CedarUserId.build(CURRENT_USER_ID), CATEGORY_ID))
        .thenReturn(new CategoryAuthority(CategoryRole.EDITOR, false));

    BackendCallResult<VersionedCategoryPermissions> result = service.updateCategoryPermissions(CATEGORY_ID,
        new CategoryPermissionRequest(), PRECONDITION);

    assertEquals(CedarErrorKey.NOT_AUTHORIZED, result.getFirstError().getErrorPack().getErrorKey());
    verify(categoryPermissions, never()).replacePermissions(any(), any(), any());
  }

  @Test
  void anAclWhoseCategoryVanishedBeforeTheWriteIsAServerError() {
    ownerMayManageGrants();
    when(categoryPermissions.replacePermissions(any(), any(), any())).thenReturn(null);

    BackendCallResult<VersionedCategoryPermissions> result = service.updateCategoryPermissions(CATEGORY_ID,
        new CategoryPermissionRequest(), PRECONDITION);

    assertTrue(result.isError());
    assertEquals(CedarErrorType.SERVER_ERROR, result.getFirstError().getErrorPack().getErrorType());
  }

  @Test
  void aValidAclIsWrittenUnderTheCallersPrecondition() {
    ownerMayManageGrants();
    VersionedCategoryPermissions written = new VersionedCategoryPermissions(new CategoryPermissions(), 5);
    when(categoryPermissions.replacePermissions(any(), any(), any())).thenReturn(written);

    BackendCallResult<VersionedCategoryPermissions> result = service.updateCategoryPermissions(CATEGORY_ID,
        new CategoryPermissionRequest(), PRECONDITION);

    assertTrue(result.isOk());
    assertSame(written, result.getPayload());
    ArgumentCaptor<CategoryPermissions> replacement = ArgumentCaptor.forClass(CategoryPermissions.class);
    verify(categoryPermissions).replacePermissions(eq(CATEGORY_ID), replacement.capture(), eq(PRECONDITION));
    assertEquals(CURRENT_USER_ID, replacement.getValue().getOwner().getId(), "the stored owner is kept");
  }

  private void ownerMayManageGrants() {
    when(categoryPermissions.getCategoryAuthority(CedarUserId.build(CURRENT_USER_ID), CATEGORY_ID))
        .thenReturn(new CategoryAuthority(null, true));
    CategoryPermissions current = new CategoryPermissions();
    current.setOwner(new CedarUserExtract(CURRENT_USER_ID, null, null, null));
    when(categoryPermissions.getVersionedPermissions(CATEGORY_ID)).thenReturn(new VersionedCategoryPermissions(current, 4));
  }

  private BackendCallResult<VersionedCategoryPermissions> transferToOther() {
    return service.transferCategoryOwnership(CATEGORY_ID, CedarUserId.build(OTHER_USER_ID), PRECONDITION);
  }

  private void assertRefused(BackendCallResult<VersionedCategoryPermissions> result, CedarErrorType type,
                             CedarErrorKey key) {
    assertTrue(result.isError());
    assertEquals(type, result.getFirstError().getErrorPack().getErrorType());
    assertEquals(key, result.getFirstError().getErrorPack().getErrorKey());
    verify(categoryPermissions, never()).transferOwnership(any(), any(), any(), any());
  }

  private static FolderServerUser user(String id) {
    FolderServerUser user = new FolderServerUser();
    user.setId(id);
    return user;
  }
}
