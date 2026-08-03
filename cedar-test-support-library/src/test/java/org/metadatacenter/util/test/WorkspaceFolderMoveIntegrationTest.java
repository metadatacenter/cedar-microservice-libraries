package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Map;

/**
 * Direct tests of folder reparenting through FolderServiceSession.moveFolder, against an
 * in-process Neo4j. The move (Neo4JProxyFolder.moveFolder) is purely structural: it deletes the
 * single incoming CONTAINS relation and creates one from the new parent. Nothing else changes,
 * because nothing else is stored: a folder's path is computed on demand from the CONTAINS chain
 * (addPathAndParentId, findFolderByPath), and permissions are resolved by walking the same chain
 * at query time. The session applies no ACL check; the resource server's command layer
 * authorizes before calling.
 */
public class WorkspaceFolderMoveIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;
  private static String user1HomePath;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    // The Redis redirection must be in place before startRedirectAndSeed builds the CedarConfig
    // singleton from the environment
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));

    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);

    FolderServiceSession user1Folders = CedarDataServices.getInstance().getFolderServiceSession(user1Context);
    FolderServerFolder home = user1Folders.findHomeFolderOf();
    user1HomeId = home.getResourceId();
    user1Folders.addPathAndParentId(home);
    user1HomePath = home.getPath();
    Assertions.assertNotNull(user1HomePath, "The home folder should have a computable path");
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getFolderServiceSession(context);
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspaceFolderMoveIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, newFolderId);
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
    return created;
  }

  @Test
  public void moveFolderReparentsAndPathsFollow() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder oldParent = createFolderUnder(user1HomeId, "Move Old Parent");
    FolderServerFolder subject = createFolderUnder(oldParent.getResourceId(), "Move Subject");
    FolderServerFolder grandchild = createFolderUnder(subject.getResourceId(), "Move Grandchild");
    FolderServerFolder newParent = createFolderUnder(user1HomeId, "Move New Parent");

    Assertions.assertTrue(user1Folders.moveFolder(subject.getResourceId(), newParent.getResourceId()),
        "Moving the subject under the sibling should succeed");

    Assertions.assertNull(
        user1Folders.findFilesystemResourceByParentFolderIdAndName(oldParent.getResourceId(), "Move Subject"),
        "The old parent should no longer list the moved folder");
    FileSystemResource listedInNewParent =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(newParent.getResourceId(), "Move Subject");
    Assertions.assertNotNull(listedInNewParent, "The new parent should list the moved folder");
    Assertions.assertEquals(subject.getId(), listedInNewParent.getId(),
        "The listing under the new parent should be the same node");

    // The path is computed from the CONTAINS chain, so it reflects the move without any update
    FolderServerFolder movedFresh = user1Folders.findFolderById(subject.getResourceId());
    user1Folders.addPathAndParentId(movedFresh);
    Assertions.assertEquals(user1HomePath + "/Move New Parent", movedFresh.getParentPath(),
        "The moved folder's parent path should be the new parent's path");
    Assertions.assertEquals(user1HomePath + "/Move New Parent/Move Subject", movedFresh.getPath(),
        "The moved folder's own path should sit under the new parent");

    // The subtree moved with its root: the grandchild resolves on the new path, not the old one
    FolderServerFolder resolvedGrandchild =
        user1Folders.findFolderByPath(user1HomePath + "/Move New Parent/Move Subject/Move Grandchild");
    Assertions.assertNotNull(resolvedGrandchild, "The grandchild should resolve through the new path");
    Assertions.assertEquals(grandchild.getId(), resolvedGrandchild.getId(),
        "The grandchild resolved through the new path should be the same node");
    Assertions.assertNull(
        user1Folders.findFolderByPath(user1HomePath + "/Move Old Parent/Move Subject/Move Grandchild"),
        "The grandchild should no longer resolve through the old path");
  }

  @Test
  public void moveFolderRefusesSelfAndDescendantTargets() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder a = createFolderUnder(user1HomeId, "Cycle Guard A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Cycle Guard B");
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Cycle Guard C");

    // The proxy rejects both degenerate targets up front: a folder can be moved neither onto
    // itself nor under its own descendant (which would detach the subtree into a cycle)
    Assertions.assertFalse(user1Folders.moveFolder(a.getResourceId(), a.getResourceId()),
        "Moving a folder onto itself should be refused");
    Assertions.assertFalse(user1Folders.moveFolder(a.getResourceId(), c.getResourceId()),
        "Moving a folder under its own grandchild should be refused");

    // The refused moves should leave the tree untouched
    FileSystemResource aStillUnderHome =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(user1HomeId, "Cycle Guard A");
    Assertions.assertNotNull(aStillUnderHome, "The top folder should still sit under the home folder");
    Assertions.assertEquals(a.getId(), aStillUnderHome.getId(),
        "The top folder under home should be the original node");
    FileSystemResource bStillUnderA =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(a.getResourceId(), "Cycle Guard B");
    Assertions.assertNotNull(bStillUnderA, "The middle folder should still sit under the top folder");
  }

  @Test
  public void movingOutOfAGrantedSubtreeRevokesInheritedAccess() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder grantedRoot = createFolderUnder(user1HomeId, "Grant Root");
    FolderServerFolder child = createFolderUnder(grantedRoot.getResourceId(), "Grant Child");
    FolderServerFolder neutral = createFolderUnder(user1HomeId, "Grant Neutral");

    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), FilesystemResourcePermission.READ));
    BackendCallResult result = CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(grantedRoot.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "The permission update should succeed");

    ResourcePermissionServiceSession user2Permissions =
        CedarDataServices.getInstance().getResourcePermissionServiceSession(user2Context);
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(child.getResourceId()),
        "Before the move, user2 should read the child through the grant on its parent");

    Assertions.assertTrue(user1Folders.moveFolder(child.getResourceId(), neutral.getResourceId()),
        "Moving the child under the ungranted sibling should succeed");

    // Access is derived from the current ancestor chain at query time; leaving the granted
    // subtree severs it, with no revocation step involved
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(child.getResourceId()),
        "After the move, user2 should no longer read the child");
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(grantedRoot.getResourceId()),
        "The grant on the original folder itself should be unaffected");
  }

}
