package org.metadatacenter.server.search.elasticsearch.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.SubmissionConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserPermissionUpdaterForSearchTest {

  private CedarConfig config;
  private SubmissionConfig submissionConfig;
  private IndexedDocumentDocument document;
  private FolderServerNodeInfo info;
  private CedarUser user;

  @BeforeEach
  void setUp() {
    config = mock(CedarConfig.class);
    submissionConfig = mock(SubmissionConfig.class);
    document = mock(IndexedDocumentDocument.class);
    info = mock(FolderServerNodeInfo.class);
    user = new CedarUser();
    user.setId("user-1");
    when(config.getSubmissionConfig()).thenReturn(submissionConfig);
    when(document.getInfo()).thenReturn(info);
    when(document.getUsers()).thenReturn(List.of());
    when(info.getType()).thenReturn(CedarResourceType.TEMPLATE);
    when(info.getOwnedBy()).thenReturn("user-1");
    when(info.getPublicationStatus()).thenReturn(BiboStatus.DRAFT);
    when(info.isLatestVersion()).thenReturn(true);
  }

  static Stream<Arguments> materializedAccess() {
    return Stream.of(
        Arguments.of(List.of(key(FilesystemResourcePermission.WRITE)), List.<CedarPermission>of(), true, true),
        Arguments.of(List.of(key(FilesystemResourcePermission.READ)), List.<CedarPermission>of(), true, false),
        Arguments.of(List.of(), List.<CedarPermission>of(), false, false),
        Arguments.of(null, List.<CedarPermission>of(), false, false),
        Arguments.of(Arrays.asList((String) null), List.<CedarPermission>of(), false, false),
        Arguments.of(List.of(), List.of(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE), true, true),
        Arguments.of(List.of(), List.of(CedarPermission.READ_NOT_READABLE_NODE), true, false)
    );
  }

  @ParameterizedTest
  @MethodSource("materializedAccess")
  void projectsMaterializedAndAdministrativeReadWriteAccess(List<String> users, List<CedarPermission> globalPermissions,
                                                             boolean canRead, boolean canWrite) {
    when(document.getUsers()).thenReturn(users);
    setGlobalPermissions(globalPermissions);

    CurrentUserResourcePermissions output = updateResource();

    assertEquals(canRead, output.isCanRead());
    assertEquals(canWrite, output.isCanWrite());
    assertEquals(canWrite, output.isCanDelete());
    assertEquals(canWrite, output.isCanShare());
  }

  static Stream<Arguments> ownershipCases() {
    return Stream.of(
        Arguments.of("user-1", List.<CedarPermission>of(), true),
        Arguments.of("user-2", List.<CedarPermission>of(), false),
        Arguments.of(null, List.<CedarPermission>of(), false),
        Arguments.of("user-2", List.of(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE), true));
  }

  @ParameterizedTest
  @MethodSource("ownershipCases")
  void changeOwnerRequiresOwnershipOrAdministrativeOverride(String ownerId, List<CedarPermission> globalPermissions,
                                                             boolean expected) {
    when(info.getOwnedBy()).thenReturn(ownerId);
    setGlobalPermissions(globalPermissions);

    assertEquals(expected, updateResource().isCanChangeOwner());
  }

  static Stream<Arguments> resourceTypeCapabilities() {
    return Stream.of(
        Arguments.of(CedarResourceType.TEMPLATE, true, true),
        Arguments.of(CedarResourceType.ELEMENT, true, false),
        Arguments.of(CedarResourceType.FIELD, true, false),
        Arguments.of(CedarResourceType.INSTANCE, true, false),
        Arguments.of(CedarResourceType.FOLDER, true, false));
  }

  @ParameterizedTest
  @MethodSource("resourceTypeCapabilities")
  void copyIsUniversalWhilePopulateIsTemplateOnly(CedarResourceType type, boolean canCopy, boolean canPopulate) {
    when(info.getType()).thenReturn(type);
    CurrentUserResourcePermissions output = updateResource();
    assertEquals(canCopy, output.isCanCopy());
    assertEquals(canPopulate, output.isCanPopulate());
  }

  static Stream<Arguments> versionStates() {
    return Stream.of(
        Arguments.of("user-2", CedarResourceType.TEMPLATE, BiboStatus.DRAFT, true,
            false, false, CedarErrorKey.VERSIONING_ONLY_BY_OWNER, CedarErrorKey.VERSIONING_ONLY_BY_OWNER),
        Arguments.of("user-1", CedarResourceType.INSTANCE, BiboStatus.DRAFT, true,
            false, false, CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE, CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.DRAFT, true,
            true, false, null, CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.PUBLISHED, true,
            false, true, CedarErrorKey.PUBLISH_ONLY_DRAFT, null),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.DRAFT, false,
            false, false, CedarErrorKey.VERSIONING_ONLY_ON_LATEST, CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.PUBLISHED, false,
            false, false, CedarErrorKey.PUBLISH_ONLY_DRAFT, CedarErrorKey.VERSIONING_ONLY_ON_LATEST),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.DRAFT, null,
            false, false, CedarErrorKey.VERSIONING_ONLY_ON_LATEST, CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED),
        Arguments.of("user-1", CedarResourceType.TEMPLATE, BiboStatus.PUBLISHED, null,
            false, false, CedarErrorKey.PUBLISH_ONLY_DRAFT, CedarErrorKey.VERSIONING_ONLY_ON_LATEST));
  }

  @ParameterizedTest
  @MethodSource("versionStates")
  void projectsVersionEligibilityAndExactFailureReasons(String owner, CedarResourceType type, BiboStatus status,
                                                        Boolean latest, boolean canPublish, boolean canDraft,
                                                        CedarErrorKey publishError, CedarErrorKey draftError) {
    when(info.getOwnedBy()).thenReturn(owner);
    when(info.getType()).thenReturn(type);
    when(info.getPublicationStatus()).thenReturn(status);
    when(info.isLatestVersion()).thenReturn(latest);

    CurrentUserResourcePermissions output = updateResource();

    assertEquals(canPublish, output.isCanPublish());
    assertEquals(canDraft, output.isCanCreateDraft());
    assertEquals(publishError, output.getPublishErrorKey());
    assertEquals(draftError, output.getCreateDraftErrorKey());
  }

  static Stream<Arguments> openTransitions() {
    return Stream.of(
        Arguments.of(true, false, true, false),
        Arguments.of(true, true, false, true),
        Arguments.of(false, false, false, false),
        Arguments.of(false, true, false, false));
  }

  @ParameterizedTest
  @MethodSource("openTransitions")
  void openTransitionsRequireWriteAndAreMutuallyExclusive(boolean writable, boolean open,
                                                           boolean canMakeOpen, boolean canMakeNotOpen) {
    when(document.getUsers()).thenReturn(writable ? List.of(key(FilesystemResourcePermission.WRITE)) : List.of());
    when(info.getIsOpen()).thenReturn(open);

    CurrentUserResourcePermissions output = updateResource();

    assertEquals(canMakeOpen, output.isCanMakeOpen());
    assertEquals(canMakeNotOpen, output.isCanMakeNotOpen());
  }

  static Stream<Arguments> submissionStates() {
    return Stream.of(
        Arguments.of(CedarResourceType.TEMPLATE, "template-1", List.of("template-1"), true, false),
        Arguments.of(CedarResourceType.INSTANCE, null, List.of("template-1"), true, false),
        Arguments.of(CedarResourceType.INSTANCE, "template-1", List.of("template-1"), true, true),
        Arguments.of(CedarResourceType.INSTANCE, "template-1", List.of("template-2"), true, false),
        Arguments.of(CedarResourceType.INSTANCE, "template-1", null, true, false),
        Arguments.of(CedarResourceType.INSTANCE, "template-1", List.of("template-1"), false, false));
  }

  @ParameterizedTest
  @MethodSource("submissionStates")
  void submitRequiresInstanceBasedOnConfiguredTemplate(CedarResourceType type, String basedOn,
                                                       List<String> submittableIds, boolean hasSubmissionConfig,
                                                       boolean expected) {
    when(info.getType()).thenReturn(type);
    when(info.getIsBasedOnId()).thenReturn(basedOn == null ? null : CedarTemplateId.build(basedOn));
    when(submissionConfig.getSubmittableTemplateIds()).thenReturn(submittableIds);
    if (!hasSubmissionConfig) {
      when(config.getSubmissionConfig()).thenReturn(null);
    }

    assertEquals(expected, updateResource().isCanSubmit());
  }

  static Stream<Arguments> folderSharingStates() {
    return Stream.of(
        Arguments.of(false, false, false, true),
        Arguments.of(true, false, false, false),
        Arguments.of(false, true, false, false),
        Arguments.of(false, false, true, false));
  }

  @ParameterizedTest
  @MethodSource("folderSharingStates")
  void writableFolderSharingExcludesRootSystemAndUserHome(boolean root, boolean system, boolean userHome,
                                                           boolean canShare) {
    when(document.getUsers()).thenReturn(List.of(key(FilesystemResourcePermission.WRITE)));
    when(info.getIsRoot()).thenReturn(root);
    when(info.getIsSystem()).thenReturn(system);
    when(info.getIsUserHome()).thenReturn(userHome);

    CurrentUserResourcePermissions output = updateFolder();

    assertTrue(output.isCanRead());
    assertTrue(output.isCanWrite());
    assertTrue(output.isCanDelete());
    assertEquals(canShare, output.isCanShare());
  }

  @Test
  void readOnlyFolderDoesNotGainWriteDeleteOrShare() {
    when(document.getUsers()).thenReturn(List.of(key(FilesystemResourcePermission.READ)));
    CurrentUserResourcePermissions output = updateFolder();
    assertTrue(output.isCanRead());
    assertFalse(output.isCanWrite());
    assertFalse(output.isCanDelete());
    assertFalse(output.isCanShare());
  }

  @Test
  void folderWithMissingMaterializedUsersProducesEmptyAccessProjection() {
    when(document.getUsers()).thenReturn(null);
    when(info.getOwnedBy()).thenReturn(null);
    CurrentUserResourcePermissions output = updateFolder();
    assertFalse(output.isCanRead());
    assertFalse(output.isCanWrite());
    assertFalse(output.isCanChangeOwner());
  }

  private CurrentUserResourcePermissions updateResource() {
    CurrentUserResourcePermissions output = new CurrentUserResourcePermissions();
    CurrentUserPermissionUpdater updater = CurrentUserPermissionUpdaterForSearchResource.get(document, user, config);
    updater.update(output);
    return output;
  }

  private CurrentUserResourcePermissions updateFolder() {
    CurrentUserResourcePermissions output = new CurrentUserResourcePermissions();
    CurrentUserPermissionUpdater updater = CurrentUserPermissionUpdaterForSearchFolder.get(document, user, config);
    updater.update(output);
    return output;
  }

  private void setGlobalPermissions(List<CedarPermission> permissions) {
    user.setPermissions(permissions.stream().map(CedarPermission::getPermissionName).toList());
  }

  private static String key(FilesystemResourcePermission permission) {
    return CedarNodeMaterializedPermissions.getKey("user-1", permission);
  }
}
