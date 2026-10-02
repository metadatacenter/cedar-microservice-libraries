package org.metadatacenter.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateInstanceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAction;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PathInfoBuilderTest {

  private CedarRequestContext context;
  private CedarUser user;
  private FolderServiceSession folderSession;
  private ResourcePermissionServiceSession permissionSession;
  private FolderServerFolder node;

  @BeforeEach
  void setUp() {
    context = mock(CedarRequestContext.class);
    user = mock(CedarUser.class);
    folderSession = mock(FolderServiceSession.class);
    permissionSession = mock(ResourcePermissionServiceSession.class);
    node = new FolderServerFolder();
    node.setId("requested-folder");
    when(context.getCedarUser()).thenReturn(user);
    when(permissionSession.getResourceAuthority(any())).thenReturn(new ResourceAuthority(null, false));
  }

  static Stream<Arguments> opennessPaths() {
    return Stream.of(
        Arguments.of(List.of(false), List.of(false)),
        Arguments.of(List.of(true), List.of(true)),
        Arguments.of(List.of(false, false, false), List.of(false, false, false)),
        Arguments.of(List.of(true, false, false), List.of(true, true, true)),
        Arguments.of(List.of(false, true, false), List.of(false, true, true)),
        Arguments.of(List.of(false, false, true), List.of(false, false, true)),
        Arguments.of(List.of(true, true, false), List.of(true, true, true)),
        Arguments.of(List.of(false, true, true), List.of(false, true, true))
    );
  }

  @ParameterizedTest
  @MethodSource("opennessPaths")
  void openStateBecomesStickyForEveryDescendant(List<Boolean> explicitOpen, List<Boolean> expectedImplicit) {
    List<FolderServerResourceExtract> path = new ArrayList<>();
    for (int i = 0; i < explicitOpen.size(); i++) {
      path.add(folder("folder-" + i, explicitOpen.get(i)));
    }
    when(folderSession.findNodePathExtract(node)).thenReturn(path);
    when(permissionSession.userHasCapability(any(), eq(ResourceCapability.READ_RESOURCE))).thenReturn(true);

    List<FolderServerResourceExtract> result =
        PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertSame(path, result);
    assertEquals(expectedImplicit, result.stream().map(FolderServerResourceExtract::getIsOpenImplicitly).toList());
  }

  @Test
  void nullOpenFlagDoesNotStartImplicitOpenness() {
    FolderServerFolderExtract first = folder("first", null);
    FolderServerFolderExtract second = folder("second", false);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(first, second));
    when(permissionSession.userHasCapability(any(), eq(ResourceCapability.READ_RESOURCE))).thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(List.of(false, false), List.of(first.getIsOpenImplicitly(), second.getIsOpenImplicitly()));
  }

  @Test
  void privilegedUserCanReadEveryNodeWithoutBackendPermissionCalls() {
    FolderServerFolderExtract root = folder("root", false); root.setRoot(true);
    FolderServerFolderExtract system = folder("system", false); system.setSystem(true);
    FolderServerTemplateExtract artifact = template("template");
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(root, system, artifact));
    when(user.has(CedarPermission.READ_NOT_READABLE_NODE)).thenReturn(true);

    List<FolderServerResourceExtract> result =
        PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(List.of(true, true, true), result.stream().map(FolderServerResourceExtract::isActiveUserCanRead).toList());
    verify(permissionSession, never()).userHasCapability(any(), any());
  }

  @Test
  void ordinaryUserCannotReadRootEvenIfBackendWouldAllowIt() {
    FolderServerFolderExtract root = folder("root", false); root.setRoot(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(root));
    when(permissionSession.userHasCapability(root.getResourceId(), ResourceCapability.READ_RESOURCE)).thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(false, root.isActiveUserCanRead());
    verify(permissionSession, never()).userHasCapability(any(), any());
  }

  @Test
  void ordinaryUserCannotReadSystemFolderEvenIfBackendWouldAllowIt() {
    FolderServerFolderExtract system = folder("system", false); system.setSystem(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(system));

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(false, system.isActiveUserCanRead());
    verify(permissionSession, never()).userHasCapability(any(), any());
  }

  static Stream<Arguments> ordinaryReadResults() {
    return Stream.of(
        Arguments.of(false, false),
        Arguments.of(true, true));
  }

  @ParameterizedTest
  @MethodSource("ordinaryReadResults")
  void ordinaryFolderVisibilityMirrorsBackendReadAccess(boolean backendRead, boolean expected) {
    FolderServerFolderExtract folder = folder("ordinary", false);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(folder));
    when(permissionSession.userHasCapability(folder.getResourceId(), ResourceCapability.READ_RESOURCE)).thenReturn(backendRead);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(expected, folder.isActiveUserCanRead());
    verify(permissionSession).userHasCapability(folder.getResourceId(), ResourceCapability.READ_RESOURCE);
  }

  @ParameterizedTest
  @MethodSource("ordinaryReadResults")
  void artifactVisibilityMirrorsBackendReadAccess(boolean backendRead, boolean expected) {
    FolderServerTemplateExtract artifact = template("template");
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(artifact));
    when(permissionSession.userHasCapability(artifact.getResourceId(), ResourceCapability.READ_RESOURCE)).thenReturn(backendRead);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(expected, artifact.isActiveUserCanRead());
    verify(permissionSession).userHasCapability(artifact.getResourceId(), ResourceCapability.READ_RESOURCE);
  }

  @Test
  void userHomeFolderUsesBackendPermissionRatherThanSystemFolderRule() {
    FolderServerFolderExtract home = folder("home", false); home.setUserHome(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(home));
    when(permissionSession.userHasCapability(home.getResourceId(), ResourceCapability.READ_RESOURCE)).thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(true, home.isActiveUserCanRead());
    verify(permissionSession).userHasCapability(home.getResourceId(), ResourceCapability.READ_RESOURCE);
  }

  @Test
  void emptyPathIsReturnedWithoutPermissionCalls() {
    List<FolderServerResourceExtract> empty = List.of();
    when(folderSession.findNodePathExtract(node)).thenReturn(empty);

    assertSame(empty, PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node));
    verify(permissionSession, never()).userHasCapability(any(), any());
  }

  @Test
  void everyPathEntryCarriesItsResourceRoleOwnershipAndCapabilities() {
    FolderServerFolderExtract folder = folder("ordinary", false);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(folder));
    when(permissionSession.getResourceAuthority(folder.getResourceId()))
        .thenReturn(new ResourceAuthority(ResourceRole.EDITOR, false));
    when(permissionSession.userHasCapability(folder.getResourceId(), ResourceCapability.READ_RESOURCE))
        .thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(ResourceRole.EDITOR, folder.getCurrentUserPermissions().getRole());
    assertEquals(false, folder.getCurrentUserPermissions().isOwner());
    assertEquals(
        new ResourceAuthority(ResourceRole.EDITOR, false)
            .capabilitiesFor(org.metadatacenter.model.CedarResourceType.FOLDER),
        folder.getCurrentUserPermissions().getCapabilities());
  }

  @Test
  void oneListedResourceCanBeDecoratedWithoutBuildingAPath() {
    FolderServerFolderExtract folder = folder("listed", false);
    ResourceAuthority authority = new ResourceAuthority(null, true);
    when(permissionSession.getResourceAuthority(folder.getResourceId())).thenReturn(authority);

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, folder);

    assertEquals(null, folder.getCurrentUserPermissions().getRole());
    assertEquals(true, folder.getCurrentUserPermissions().isOwner());
    assertEquals(authority.capabilitiesFor(org.metadatacenter.model.CedarResourceType.FOLDER),
        folder.getCurrentUserPermissions().getCapabilities());
  }

  /**
   * A folder listing offered no actions at all: it carried the capabilities and never asked the
   * action policy. Every action-gated menu entry was disabled, so a resource made not open could not
   * be made open again from the listing.
   */
  static Stream<Arguments> listedOpenStates() {
    return Stream.of(
        Arguments.of(true, ResourceAction.DISABLE_OPENVIEW),
        Arguments.of(false, ResourceAction.ENABLE_OPENVIEW),
        // What a make-not-open left before it wrote the flag as false: no flag at all.
        Arguments.of(null, ResourceAction.ENABLE_OPENVIEW));
  }

  @ParameterizedTest
  @MethodSource("listedOpenStates")
  void aListedResourceOffersTheOpenViewChangeItsStateAllows(Boolean open, ResourceAction offered) {
    FolderServerTemplateInstanceExtract instance = instance("instance", open);
    when(permissionSession.getResourceAuthority(instance.getResourceId())).thenReturn(new ResourceAuthority(null, true));

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, instance);

    Set<ResourceAction> actions = instance.getCurrentUserPermissions().getAvailableActions();
    assertTrue(actions.contains(offered), actions.toString());
    assertFalse(actions.contains(offered == ResourceAction.ENABLE_OPENVIEW
        ? ResourceAction.DISABLE_OPENVIEW : ResourceAction.ENABLE_OPENVIEW), actions.toString());
  }

  @Test
  void aListedResourceWithoutOpenViewAuthorityOffersNeitherChange() {
    FolderServerTemplateInstanceExtract instance = instance("instance", false);
    when(permissionSession.getResourceAuthority(instance.getResourceId()))
        .thenReturn(new ResourceAuthority(ResourceRole.VIEWER, false));

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, instance);

    Set<ResourceAction> actions = instance.getCurrentUserPermissions().getAvailableActions();
    assertFalse(actions.contains(ResourceAction.ENABLE_OPENVIEW), actions.toString());
    assertFalse(actions.contains(ResourceAction.DISABLE_OPENVIEW), actions.toString());
  }

  @Test
  void aListedTemplateOffersCopyAndPopulate() {
    FolderServerTemplateExtract template = template("template");
    when(permissionSession.getResourceAuthority(template.getResourceId()))
        .thenReturn(new ResourceAuthority(ResourceRole.VIEWER, false));

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, template);

    Set<ResourceAction> actions = template.getCurrentUserPermissions().getAvailableActions();
    assertTrue(actions.containsAll(Set.of(ResourceAction.COPY_FROM_RESOURCE, ResourceAction.POPULATE)),
        actions.toString());
  }

  static Stream<Arguments> listedVersionStates() {
    return Stream.of(
        Arguments.of(true, "bibo:draft", true, Set.of(ResourceAction.PUBLISH), null, CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED),
        Arguments.of(true, "bibo:published", true, Set.of(ResourceAction.CREATE_DRAFT), CedarErrorKey.PUBLISH_ONLY_DRAFT, null),
        Arguments.of(true, "bibo:published", false, Set.of(), CedarErrorKey.PUBLISH_ONLY_DRAFT, CedarErrorKey.VERSIONING_ONLY_ON_LATEST),
        Arguments.of(true, "bibo:draft", false, Set.of(), CedarErrorKey.VERSIONING_ONLY_ON_LATEST, CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED),
        Arguments.of(false, "bibo:draft", true, Set.of(), CedarErrorKey.VERSIONING_ONLY_BY_OWNER, CedarErrorKey.VERSIONING_ONLY_BY_OWNER));
  }

  @ParameterizedTest
  @MethodSource("listedVersionStates")
  void aListedTemplateOffersTheVersioningItsStateAllows(boolean owner, String status, boolean latest,
                                                        Set<ResourceAction> offered, CedarErrorKey publishError,
                                                        CedarErrorKey draftError) {
    FolderServerTemplateExtract template = template("template");
    template.setPublicationStatus(status);
    template.setLatestVersion(latest);
    when(permissionSession.getResourceAuthority(template.getResourceId()))
        .thenReturn(new ResourceAuthority(owner ? null : ResourceRole.EDITOR, owner));

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, template);

    CurrentUserResourcePermissions permissions = template.getCurrentUserPermissions();
    Set<ResourceAction> versioning = permissions.getAvailableActions().stream()
        .filter(a -> a == ResourceAction.PUBLISH || a == ResourceAction.CREATE_DRAFT)
        .collect(Collectors.toSet());
    assertEquals(offered, versioning);
    assertEquals(publishError, permissions.getPublishErrorKey());
    assertEquals(draftError, permissions.getCreateDraftErrorKey());
  }

  @Test
  void aListedInstanceIsNotVersioned() {
    FolderServerTemplateInstanceExtract instance = instance("instance", false);
    when(permissionSession.getResourceAuthority(instance.getResourceId())).thenReturn(new ResourceAuthority(null, true));

    PathInfoBuilder.addCurrentUserPermissions(permissionSession, instance);

    CurrentUserResourcePermissions permissions = instance.getCurrentUserPermissions();
    assertFalse(permissions.getAvailableActions().contains(ResourceAction.PUBLISH));
    assertFalse(permissions.getAvailableActions().contains(ResourceAction.CREATE_DRAFT));
    assertEquals(CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE, permissions.getPublishErrorKey());
    assertEquals(CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE, permissions.getCreateDraftErrorKey());
  }

  @Test
  void theUndecoratedPathIsWhatTheGraphReturned() {
    List<FolderServerResourceExtract> path = List.of(folder("root", false), folder("parent", false));
    when(folderSession.findNodePathExtract(node)).thenReturn(new ArrayList<>(path));

    assertEquals(path, PathInfoBuilder.getResourcePath(folderSession, node));
  }

  @Test
  void theUndecoratedPathAsksThePermissionServiceNothing() {
    when(folderSession.findNodePathExtract(node))
        .thenReturn(new ArrayList<>(List.of(folder("root", false), folder("a", false), folder("b", false))));

    PathInfoBuilder.getResourcePath(folderSession, node);

    // This is the whole point of the method: the decorated path costs several graph queries per
    // element, and indexing pays it for every resource in the repository to read one identifier.
    verifyNoInteractions(permissionSession);
  }

  private static FolderServerFolderExtract folder(String id, Boolean open) {
    FolderServerFolderExtract extract = new FolderServerFolderExtract();
    extract.setId(id);
    extract.setIsOpen(open);
    return extract;
  }

  private static FolderServerTemplateExtract template(String id) {
    FolderServerTemplateExtract extract = new FolderServerTemplateExtract();
    extract.setId(id);
    return extract;
  }

  private static FolderServerTemplateInstanceExtract instance(String id, Boolean open) {
    FolderServerTemplateInstanceExtract extract = new FolderServerTemplateInstanceExtract();
    extract.setId(id);
    extract.setIsOpen(open);
    return extract;
  }
}
