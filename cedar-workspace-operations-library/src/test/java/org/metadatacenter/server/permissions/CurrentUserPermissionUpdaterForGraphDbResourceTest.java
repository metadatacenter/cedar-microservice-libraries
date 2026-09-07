package org.metadatacenter.server.permissions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.SubmissionConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithOpenFlag;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAction;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Matrix for projecting artifact authority into capabilities and context-dependent actions. */
class CurrentUserPermissionUpdaterForGraphDbResourceTest {

  @ParameterizedTest
  @MethodSource("authorities")
  void projectsEveryRoleAndOwnershipCombination(ResourceAuthority authority,
      ResourceRole effectiveRole, boolean canRead, boolean canEdit, boolean canManage,
      boolean canTransferOwnership) {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions resource = f.resource(CedarResourceType.TEMPLATE);
    when(f.permissionSession.getResourceAuthority(resource.getResourceId())).thenReturn(authority);

    CurrentUserResourcePermissions result = f.update(resource);

    assertEquals(effectiveRole, result.getCurrentUserRole());
    assertEquals(effectiveRole, result.getRole());
    assertEquals(authority.owner(), result.isOwner());
    assertEquals(canRead, result.isCanRead());
    assertEquals(canEdit, result.isCanEdit());
    assertFalse(result.isCanCreate());
    assertEquals(canManage, result.isCanWrite());
    assertEquals(canEdit, result.isCanDelete());
    assertEquals(canRead, result.isCanCopy());
    assertEquals(canManage, result.isCanManageGrants());
    assertEquals(canManage, result.isCanMove());
    assertEquals(canManage, result.isCanManageOpenView());
    assertEquals(canTransferOwnership, result.isCanTransferOwnership());
    assertFalse(result.getCapabilities().contains(ResourceCapability.LIST_FOLDER_CONTENTS));
    assertFalse(result.getCapabilities().contains(ResourceCapability.CREATE_IN_FOLDER));
    assertFalse(result.getCapabilities().contains(ResourceCapability.COPY_INTO_FOLDER));
    assertEquals(artifactCapabilities(canRead, canEdit, canManage, canTransferOwnership),
        result.getCapabilities());
    assertEquals(canRead
            ? Set.of(ResourceAction.COPY_FROM_RESOURCE, ResourceAction.POPULATE)
            : Set.of(),
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

  private static Set<ResourceCapability> artifactCapabilities(
      boolean canRead, boolean canEdit, boolean canManage, boolean canTransferOwnership) {
    Set<ResourceCapability> capabilities = new LinkedHashSet<>();
    if (canRead) {
      capabilities.add(ResourceCapability.READ_RESOURCE);
    }
    if (canEdit) {
      capabilities.add(ResourceCapability.UPDATE_RESOURCE);
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

  @Test
  void versioningDenialSuppliesTheSameGateReasonAndSkipsSpecificChecks() {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions resource = f.resource(CedarResourceType.TEMPLATE);
    when(f.versionSession.userCanPerformVersioning(resource)).thenReturn(
        OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_BY_OWNER));

    CurrentUserResourcePermissions result = f.update(resource);

    assertFalse(result.isCanPublish());
    assertFalse(result.isCanCreateDraft());
    assertEquals(CedarErrorKey.VERSIONING_ONLY_BY_OWNER, result.getPublishErrorKey());
    assertEquals(CedarErrorKey.VERSIONING_ONLY_BY_OWNER, result.getCreateDraftErrorKey());
    verify(f.versionSession, never()).resourceCanBePublished(resource);
    verify(f.versionSession, never()).resourceCanBeDrafted(resource);
  }

  @ParameterizedTest
  @MethodSource("publishAndDraftOutcomes")
  void publishAndDraftAreProjectedIndependently(boolean publish, boolean draft) {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions resource = f.resource(CedarResourceType.TEMPLATE);
    when(f.versionSession.resourceCanBePublished(resource)).thenReturn(publish
        ? OutcomeWithReason.positive() : OutcomeWithReason.negative(CedarErrorKey.PUBLISH_ONLY_DRAFT));
    when(f.versionSession.resourceCanBeDrafted(resource)).thenReturn(draft
        ? OutcomeWithReason.positive() : OutcomeWithReason.negative(CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED));

    CurrentUserResourcePermissions result = f.update(resource);

    assertEquals(publish, result.isCanPublish());
    assertEquals(draft, result.isCanCreateDraft());
    assertEquals(publish ? null : CedarErrorKey.PUBLISH_ONLY_DRAFT, result.getPublishErrorKey());
    assertEquals(draft ? null : CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED,
        result.getCreateDraftErrorKey());
  }

  @Test
  void templatesArePopulatable() {
    Fixture f = new Fixture();
    when(f.permissionSession.getResourceAuthority(any()))
        .thenReturn(new ResourceAuthority(ResourceRole.VIEWER, false));
    assertTrue(f.update(f.resource(CedarResourceType.TEMPLATE)).isCanPopulate());
  }

  @ParameterizedTest
  @ValueSource(strings = {"FIELD", "ELEMENT"})
  void nonTemplatesAreNotPopulatable(String typeName) {
    Fixture f = new Fixture();
    assertFalse(f.update(f.resource(CedarResourceType.valueOf(typeName))).isCanPopulate());
  }

  @Test
  void instanceBasedOnConfiguredTemplateIsSubmittable() {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions instance = f.instance("template-1");
    when(f.submissionConfig.getSubmittableTemplateIds()).thenReturn(List.of("template-1"));

    assertTrue(f.update(instance).isCanSubmit());
  }

  @Test
  void instanceBasedOnOtherTemplateIsNotSubmittable() {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions instance = f.instance("template-2");
    when(f.submissionConfig.getSubmittableTemplateIds()).thenReturn(List.of("template-1"));

    assertFalse(f.update(instance).isCanSubmit());
  }

  @Test
  void instanceWithoutBasedOnIsNotSubmittable() {
    Fixture f = new Fixture();
    assertFalse(f.update(f.instance(null)).isCanSubmit());
  }

  @Test
  void nullSubmissionAllowlistMakesInstanceNotSubmittable() {
    Fixture f = new Fixture();
    FilesystemResourceWithCurrentUserPermissions instance = f.instance("template-1");
    when(f.submissionConfig.getSubmittableTemplateIds()).thenReturn(null);

    assertFalse(f.update(instance).isCanSubmit());
  }

  @Test
  void aReadableResourceMayBeUsedAsACopySource() {
    Fixture f = new Fixture();
    when(f.permissionSession.getResourceAuthority(any()))
        .thenReturn(new ResourceAuthority(ResourceRole.VIEWER, false));
    assertTrue(f.update(f.resource(CedarResourceType.FIELD)).isCanCopy());
  }

  @Test
  void anUnreadableResourceMayNotBeUsedAsACopySource() {
    Fixture f = new Fixture();
    assertFalse(f.update(f.resource(CedarResourceType.FIELD)).isCanCopy());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void openResourcesExposeExactlyTheOppositeTransition(boolean open) {
    Fixture f = new Fixture();
    when(f.permissionSession.getResourceAuthority(any()))
        .thenReturn(new ResourceAuthority(ResourceRole.MANAGER, false));
    FilesystemResourceWithCurrentUserPermissions resource = f.openResource(open);

    CurrentUserResourcePermissions result = f.update(resource);

    assertEquals(!open, result.isCanMakeOpen());
    assertEquals(open, result.isCanMakeNotOpen());
  }

  @Test
  void resourcesWithoutOpenFlagExposeNeitherTransition() {
    Fixture f = new Fixture();
    CurrentUserResourcePermissions result = f.update(f.resource(CedarResourceType.FIELD));
    assertFalse(result.isCanMakeOpen());
    assertFalse(result.isCanMakeNotOpen());
  }

  private static Stream<Arguments> publishAndDraftOutcomes() {
    return Stream.of(
        Arguments.of(true, true), Arguments.of(true, false),
        Arguments.of(false, true), Arguments.of(false, false));
  }

  private static final class Fixture {
    private final ResourcePermissionServiceSession permissionSession = mock(ResourcePermissionServiceSession.class);
    private final VersionServiceSession versionSession = mock(VersionServiceSession.class);
    private final CedarConfig cedarConfig = mock(CedarConfig.class);
    private final SubmissionConfig submissionConfig = mock(SubmissionConfig.class);

    private Fixture() {
      when(cedarConfig.getSubmissionConfig()).thenReturn(submissionConfig);
      when(permissionSession.getResourceAuthority(any())).thenReturn(new ResourceAuthority(null, false));
    }

    private FilesystemResourceWithCurrentUserPermissions resource(CedarResourceType type, Class<?>... extras) {
      var settings = withSettings();
      if (extras.length > 0) {
        settings.extraInterfaces(extras);
      }
      FilesystemResourceWithCurrentUserPermissions resource = mock(
          FilesystemResourceWithCurrentUserPermissions.class, settings);
      CedarFilesystemResourceId id = type == CedarResourceType.INSTANCE
          ? CedarTemplateInstanceId.build("instance-1") : CedarTemplateId.build("resource-1");
      when(resource.getResourceId()).thenReturn(id);
      when(resource.getType()).thenReturn(type);
      when(versionSession.userCanPerformVersioning(resource)).thenReturn(OutcomeWithReason.positive());
      when(versionSession.resourceCanBePublished(resource)).thenReturn(
          OutcomeWithReason.negative(CedarErrorKey.PUBLISH_ONLY_DRAFT));
      when(versionSession.resourceCanBeDrafted(resource)).thenReturn(
          OutcomeWithReason.negative(CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED));
      return resource;
    }

    private FilesystemResourceWithCurrentUserPermissions instance(String basedOnId) {
      FilesystemResourceWithCurrentUserPermissions resource = resource(
          CedarResourceType.INSTANCE, InstanceArtifactWithIsBasedOn.class);
      CedarTemplateId basedOn = basedOnId == null ? null : CedarTemplateId.build(basedOnId);
      when(((InstanceArtifactWithIsBasedOn) resource).getIsBasedOn()).thenReturn(basedOn);
      return resource;
    }

    private FilesystemResourceWithCurrentUserPermissions openResource(boolean open) {
      FilesystemResourceWithCurrentUserPermissions resource = resource(
          CedarResourceType.TEMPLATE, ResourceWithOpenFlag.class);
      when(((ResourceWithOpenFlag) resource).isOpen()).thenReturn(open);
      return resource;
    }

    private CurrentUserResourcePermissions update(FilesystemResourceWithCurrentUserPermissions resource) {
      ResourceAuthority authority = permissionSession.getResourceAuthority(resource.getResourceId());
      Set<ResourceCapability> capabilities = authority.capabilitiesFor(resource.getType());
      when(permissionSession.getResourceCapabilities(resource.getResourceId()))
          .thenReturn(capabilities);
      CurrentUserResourcePermissions result = new CurrentUserResourcePermissions();
      CurrentUserPermissionUpdaterForGraphDbResource.get(
          permissionSession, versionSession, cedarConfig, resource).update(result);
      return result;
    }
  }
}
