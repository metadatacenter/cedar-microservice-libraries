package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Neo4JUserSessionVersionServiceTest {

  private Neo4JProxies proxies;
  private Neo4JProxyFilesystemResource filesystemProxy;
  private Neo4JProxyVersion versionProxy;
  private VersionServiceSession service;

  @BeforeEach
  void setUp() {
    CedarConfig config = mock(CedarConfig.class);
    when(config.getLinkedDataUtil()).thenReturn(mock(LinkedDataUtil.class));
    proxies = mock(Neo4JProxies.class);
    filesystemProxy = mock(Neo4JProxyFilesystemResource.class);
    versionProxy = mock(Neo4JProxyVersion.class);
    when(proxies.filesystemResource()).thenReturn(filesystemProxy);
    when(proxies.version()).thenReturn(versionProxy);
    CedarUser currentUser = new CedarUser(); currentUser.setId("user-current");
    service = Neo4JUserSessionVersionService.get(config, proxies, currentUser, "global", "local");
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, names = {"FIELD", "ELEMENT", "TEMPLATE"})
  void ownerCanVersionEveryVersionedArtifactType(CedarResourceType type) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(type, BiboStatus.DRAFT);
    owner("user-current");

    assertPositive(service.userCanPerformVersioning(resource));
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, names = {"FOLDER", "INSTANCE", "USER", "GROUP", "CATEGORY"})
  void ownerCannotVersionNonVersionedResourceTypes(CedarResourceType type) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(type, BiboStatus.DRAFT);
    owner("user-current");

    assertNegative(service.userCanPerformVersioning(resource), CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE);
  }

  @ParameterizedTest
  @MethodSource("nonOwnerCases")
  void ownershipFailureTakesPrecedenceOverResourceType(String ownerId, CedarResourceType type) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(type, BiboStatus.DRAFT);
    owner(ownerId);

    assertNegative(service.userCanPerformVersioning(resource), CedarErrorKey.VERSIONING_ONLY_BY_OWNER);
  }

  static Stream<Arguments> nonOwnerCases() {
    return Stream.of(
        Arguments.of("user-other", CedarResourceType.TEMPLATE),
        Arguments.of("user-other", CedarResourceType.FOLDER),
        Arguments.of((String) null, CedarResourceType.TEMPLATE));
  }

  @Test
  void missingOwnerIsRejectedAsNotOwned() {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, BiboStatus.DRAFT);
    when(filesystemProxy.getFilesystemResourceOwner(resource.getResourceId())).thenReturn(null);

    assertNegative(service.userCanPerformVersioning(resource), CedarErrorKey.VERSIONING_ONLY_BY_OWNER);
  }

  @Test
  void latestDraftCanBePublished() {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, BiboStatus.DRAFT);
    noSuccessor();

    assertPositive(service.resourceCanBePublished(resource));
  }

  @ParameterizedTest
  @MethodSource("nonDraftStatuses")
  void onlyDraftCanBePublished(BiboStatus status) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, status);

    assertNegative(service.resourceCanBePublished(resource), CedarErrorKey.PUBLISH_ONLY_DRAFT);
    verify(versionProxy, never()).resourceWithPreviousVersion(any());
  }

  static Stream<Arguments> nonDraftStatuses() {
    return Stream.of(Arguments.of(BiboStatus.PUBLISHED), Arguments.of((BiboStatus) null));
  }

  @Test
  void supersededDraftCannotBePublished() {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, BiboStatus.DRAFT);
    successorExists();

    assertNegative(service.resourceCanBePublished(resource), CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
  }

  @Test
  void latestPublishedArtifactCanCreateDraft() {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, BiboStatus.PUBLISHED);
    noSuccessor();

    assertPositive(service.resourceCanBeDrafted(resource));
  }

  @ParameterizedTest
  @MethodSource("nonPublishedStatuses")
  void draftCreationRequiresPublishedSource(BiboStatus status) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, status);

    assertNegative(service.resourceCanBeDrafted(resource), CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED);
    verify(versionProxy, never()).resourceWithPreviousVersion(any());
  }

  static Stream<Arguments> nonPublishedStatuses() {
    return Stream.of(Arguments.of(BiboStatus.DRAFT), Arguments.of((BiboStatus) null));
  }

  @Test
  void supersededPublishedArtifactCannotCreateDraft() {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(CedarResourceType.TEMPLATE, BiboStatus.PUBLISHED);
    successorExists();

    assertNegative(service.resourceCanBeDrafted(resource), CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, names = {"FIELD", "ELEMENT", "TEMPLATE"})
  void successorLookupUsesTypedSchemaArtifactIdentity(CedarResourceType type) {
    FilesystemResourceWithCurrentUserPermissions<?> resource = resource(type, BiboStatus.DRAFT);
    noSuccessor();

    service.resourceCanBePublished(resource);

    verify(versionProxy).resourceWithPreviousVersion(CedarSchemaArtifactId.build("resource-1", type));
  }

  private void owner(String ownerId) {
    FolderServerUser owner = new FolderServerUser(); owner.setId(ownerId);
    when(filesystemProxy.getFilesystemResourceOwner(any())).thenReturn(owner);
  }

  private void noSuccessor() {
    when(versionProxy.resourceWithPreviousVersion(any())).thenReturn(null);
  }

  private void successorExists() {
    when(versionProxy.resourceWithPreviousVersion(any())).thenReturn(mock(FolderServerArtifact.class));
  }

  private static FilesystemResourceWithCurrentUserPermissions<?> resource(CedarResourceType type, BiboStatus status) {
    FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus resource =
        mock(FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus.class);
    when(resource.getId()).thenReturn("resource-1");
    when(resource.getType()).thenReturn(type);
    when(resource.getResourceId()).thenReturn(mock(CedarFilesystemResourceId.class));
    when(resource.getPublicationStatus()).thenReturn(status);
    return resource;
  }

  private static void assertPositive(OutcomeWithReason outcome) {
    assertTrue(outcome.isPositive());
    assertEquals(null, outcome.getReason());
  }

  private static void assertNegative(OutcomeWithReason outcome, CedarErrorKey reason) {
    assertTrue(outcome.isNegative());
    assertEquals(reason, outcome.getReason());
  }
}
