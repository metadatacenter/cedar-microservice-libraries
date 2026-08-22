package org.metadatacenter.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    when(permissionSession.userHasReadAccessToResource(any())).thenReturn(true);

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
    when(permissionSession.userHasReadAccessToResource(any())).thenReturn(true);

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
    verify(permissionSession, never()).userHasReadAccessToResource(any());
  }

  @Test
  void ordinaryUserCannotReadRootEvenIfBackendWouldAllowIt() {
    FolderServerFolderExtract root = folder("root", false); root.setRoot(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(root));
    when(permissionSession.userHasReadAccessToResource(root.getResourceId())).thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(false, root.isActiveUserCanRead());
    verify(permissionSession, never()).userHasReadAccessToResource(any());
  }

  @Test
  void ordinaryUserCannotReadSystemFolderEvenIfBackendWouldAllowIt() {
    FolderServerFolderExtract system = folder("system", false); system.setSystem(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(system));

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(false, system.isActiveUserCanRead());
    verify(permissionSession, never()).userHasReadAccessToResource(any());
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
    when(permissionSession.userHasReadAccessToResource(folder.getResourceId())).thenReturn(backendRead);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(expected, folder.isActiveUserCanRead());
    verify(permissionSession).userHasReadAccessToResource(folder.getResourceId());
  }

  @ParameterizedTest
  @MethodSource("ordinaryReadResults")
  void artifactVisibilityMirrorsBackendReadAccess(boolean backendRead, boolean expected) {
    FolderServerTemplateExtract artifact = template("template");
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(artifact));
    when(permissionSession.userHasReadAccessToResource(artifact.getResourceId())).thenReturn(backendRead);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(expected, artifact.isActiveUserCanRead());
    verify(permissionSession).userHasReadAccessToResource(artifact.getResourceId());
  }

  @Test
  void userHomeFolderUsesBackendPermissionRatherThanSystemFolderRule() {
    FolderServerFolderExtract home = folder("home", false); home.setUserHome(true);
    when(folderSession.findNodePathExtract(node)).thenReturn(List.of(home));
    when(permissionSession.userHasReadAccessToResource(home.getResourceId())).thenReturn(true);

    PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node);

    assertEquals(true, home.isActiveUserCanRead());
    verify(permissionSession).userHasReadAccessToResource(home.getResourceId());
  }

  @Test
  void emptyPathIsReturnedWithoutPermissionCalls() {
    List<FolderServerResourceExtract> empty = List.of();
    when(folderSession.findNodePathExtract(node)).thenReturn(empty);

    assertSame(empty, PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, node));
    verify(permissionSession, never()).userHasReadAccessToResource(any());
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
}
