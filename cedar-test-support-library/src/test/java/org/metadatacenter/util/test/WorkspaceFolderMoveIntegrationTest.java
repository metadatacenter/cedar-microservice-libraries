package org.metadatacenter.util.test;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
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

  @BeforeClass
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

    FolderServiceSession user1Folders = CedarDataServices.getFolderServiceSession(user1Context);
    FolderServerFolder home = user1Folders.findHomeFolderOf();
    user1HomeId = home.getResourceId();
    user1Folders.addPathAndParentId(home);
    user1HomePath = home.getPath();
    Assert.assertNotNull("The home folder should have a computable path", user1HomePath);
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getFolderServiceSession(context);
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspaceFolderMoveIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, newFolderId);
    Assert.assertNotNull("The folder '" + name + "' should be created", created);
    return created;
  }

  @Test
  public void moveFolderReparentsAndPathsFollow() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder oldParent = createFolderUnder(user1HomeId, "Move Old Parent");
    FolderServerFolder subject = createFolderUnder(oldParent.getResourceId(), "Move Subject");
    FolderServerFolder grandchild = createFolderUnder(subject.getResourceId(), "Move Grandchild");
    FolderServerFolder newParent = createFolderUnder(user1HomeId, "Move New Parent");

    Assert.assertTrue("Moving the subject under the sibling should succeed",
        user1Folders.moveFolder(subject.getResourceId(), newParent.getResourceId()));

    Assert.assertNull("The old parent should no longer list the moved folder",
        user1Folders.findFilesystemResourceByParentFolderIdAndName(oldParent.getResourceId(), "Move Subject"));
    FileSystemResource listedInNewParent =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(newParent.getResourceId(), "Move Subject");
    Assert.assertNotNull("The new parent should list the moved folder", listedInNewParent);
    Assert.assertEquals("The listing under the new parent should be the same node",
        subject.getId(), listedInNewParent.getId());

    // The path is computed from the CONTAINS chain, so it reflects the move without any update
    FolderServerFolder movedFresh = user1Folders.findFolderById(subject.getResourceId());
    user1Folders.addPathAndParentId(movedFresh);
    Assert.assertEquals("The moved folder's parent path should be the new parent's path",
        user1HomePath + "/Move New Parent", movedFresh.getParentPath());
    Assert.assertEquals("The moved folder's own path should sit under the new parent",
        user1HomePath + "/Move New Parent/Move Subject", movedFresh.getPath());

    // The subtree moved with its root: the grandchild resolves on the new path, not the old one
    FolderServerFolder resolvedGrandchild =
        user1Folders.findFolderByPath(user1HomePath + "/Move New Parent/Move Subject/Move Grandchild");
    Assert.assertNotNull("The grandchild should resolve through the new path", resolvedGrandchild);
    Assert.assertEquals("The grandchild resolved through the new path should be the same node",
        grandchild.getId(), resolvedGrandchild.getId());
    Assert.assertNull("The grandchild should no longer resolve through the old path",
        user1Folders.findFolderByPath(user1HomePath + "/Move Old Parent/Move Subject/Move Grandchild"));
  }

  @Test
  public void moveFolderRefusesSelfAndDescendantTargets() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder a = createFolderUnder(user1HomeId, "Cycle Guard A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Cycle Guard B");
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Cycle Guard C");

    // The proxy rejects both degenerate targets up front: a folder can be moved neither onto
    // itself nor under its own descendant (which would detach the subtree into a cycle)
    Assert.assertFalse("Moving a folder onto itself should be refused",
        user1Folders.moveFolder(a.getResourceId(), a.getResourceId()));
    Assert.assertFalse("Moving a folder under its own grandchild should be refused",
        user1Folders.moveFolder(a.getResourceId(), c.getResourceId()));

    // The refused moves should leave the tree untouched
    FileSystemResource aStillUnderHome =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(user1HomeId, "Cycle Guard A");
    Assert.assertNotNull("The top folder should still sit under the home folder", aStillUnderHome);
    Assert.assertEquals("The top folder under home should be the original node", a.getId(), aStillUnderHome.getId());
    FileSystemResource bStillUnderA =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(a.getResourceId(), "Cycle Guard B");
    Assert.assertNotNull("The middle folder should still sit under the top folder", bStillUnderA);
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
    BackendCallResult result = CedarDataServices.getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(grantedRoot.getResourceId(), request);
    Assert.assertFalse("The permission update should succeed", result.isError());

    ResourcePermissionServiceSession user2Permissions =
        CedarDataServices.getResourcePermissionServiceSession(user2Context);
    Assert.assertTrue("Before the move, user2 should read the child through the grant on its parent",
        user2Permissions.userHasReadAccessToResource(child.getResourceId()));

    Assert.assertTrue("Moving the child under the ungranted sibling should succeed",
        user1Folders.moveFolder(child.getResourceId(), neutral.getResourceId()));

    // Access is derived from the current ancestor chain at query time; leaving the granted
    // subtree severs it, with no revocation step involved
    Assert.assertFalse("After the move, user2 should no longer read the child",
        user2Permissions.userHasReadAccessToResource(child.getResourceId()));
    Assert.assertTrue("The grant on the original folder itself should be unaffected",
        user2Permissions.userHasReadAccessToResource(grantedRoot.getResourceId()));
  }

}
