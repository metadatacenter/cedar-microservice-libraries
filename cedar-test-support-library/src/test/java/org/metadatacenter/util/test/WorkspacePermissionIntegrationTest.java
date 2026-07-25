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
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUserRequest;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroup;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.HashMap;
import java.util.Map;

/**
 * Direct tests of the workspace graph layer against an in-process Neo4j: the ACL evaluation in
 * ResourcePermissionServiceSession (owner, direct user grant, group-resolved grant) and the
 * Cypher-built folder operations in FolderServiceSession. No server is booted; the sessions are
 * constructed the way the applications construct them, from a request context carrying the test
 * user. Redis is redirected to a dead port: the query-logging queue writes are best-effort, and
 * this enforces that no session operation under test depends on a live Redis.
 */
public class WorkspacePermissionIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;

  @BeforeClass
  public static void oneTimeSetUp() throws Exception {
    // The Redis redirection must be in place before startRedirectAndSeed builds the CedarConfig
    // singleton from the environment
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    // Returns the singleton instance startRedirectAndSeed already built
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));

    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);

    user1HomeId = CedarDataServices.getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getFolderServiceSession(context);
  }

  private static ResourcePermissionServiceSession permissionsOf(CedarRequestContext context) {
    return CedarDataServices.getResourcePermissionServiceSession(context);
  }

  /**
   * Creates a folder under user1's home through the real Cypher create path, owned by user1.
   */
  private static FolderServerFolder createFolderUnderUser1Home(String name) {
    return createFolderUnder(user1HomeId, name);
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspacePermissionIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, newFolderId);
    Assert.assertNotNull("The folder '" + name + "' should be created", created);
    return created;
  }

  /**
   * A permission update request keeping user1 as owner; the validator rejects a request without
   * an owner, and only an unchanged owner needs no ownership-transfer authority.
   */
  private static ResourcePermissionsRequest requestOwnedByUser1() {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    return request;
  }

  private static void updatePermissionsAsUser1(FolderServerFolder folder, ResourcePermissionsRequest request) {
    BackendCallResult result = permissionsOf(user1Context).updateResourcePermissions(folder.getResourceId(), request);
    if (result.isError()) {
      Assert.fail("The permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  @Test
  public void ownerHasReadAndWriteAccessToHomeFolder() {
    ResourcePermissionServiceSession user1Permissions = permissionsOf(user1Context);
    Assert.assertTrue("The owner should have read access to the home folder",
        user1Permissions.userHasReadAccessToResource(user1HomeId));
    Assert.assertTrue("The owner should have write access to the home folder",
        user1Permissions.userHasWriteAccessToResource(user1HomeId));
    Assert.assertTrue("The home folder should report user1 as its owner",
        user1Permissions.userIsOwnerOfResource(user1HomeId));
  }

  @Test
  public void strangerHasNoAccessToHomeFolder() {
    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assert.assertFalse("A stranger should have no read access to another user's home folder",
        user2Permissions.userHasReadAccessToResource(user1HomeId));
    Assert.assertFalse("A stranger should have no write access to another user's home folder",
        user2Permissions.userHasWriteAccessToResource(user1HomeId));
    Assert.assertFalse("A stranger should not be reported as the owner of another user's home folder",
        user2Permissions.userIsOwnerOfResource(user1HomeId));
  }

  @Test
  public void directUserGrantGivesReadButNotWrite() {
    FolderServerFolder folder = createFolderUnderUser1Home("Direct Grant Folder");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assert.assertFalse("Before the grant, user2 should have no read access",
        user2Permissions.userHasReadAccessToResource(folder.getResourceId()));

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), FilesystemResourcePermission.READ));
    updatePermissionsAsUser1(folder, request);

    Assert.assertTrue("After a READ grant, user2 should have read access",
        user2Permissions.userHasReadAccessToResource(folder.getResourceId()));
    Assert.assertFalse("A READ grant should not confer write access",
        user2Permissions.userHasWriteAccessToResource(folder.getResourceId()));
    Assert.assertFalse("A READ grant should not confer ownership",
        user2Permissions.userIsOwnerOfResource(folder.getResourceId()));
  }

  @Test
  public void groupWriteGrantResolvesThroughMembership() {
    FolderServerFolder folder = createFolderUnderUser1Home("Group Grant Folder");

    GroupServiceSession user1Groups = CedarDataServices.getGroupServiceSession(user1Context);
    FolderServerGroup group = user1Groups.createGroup("workspace-integration-test-group",
        "Group for the group-resolved ACL test");
    Assert.assertNotNull("The group should be created", group);
    Assert.assertTrue("The creator should administer the new group",
        user1Groups.userAdministersGroup(group.getResourceId()));

    // Membership updates replace the full member and administrator sets, so the request lists
    // user1 (creator, administrator) alongside the new member user2
    CedarGroupUsersRequest membership = new CedarGroupUsersRequest();
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    BackendCallResult membershipResult = user1Groups.updateGroupUsers(group.getResourceId(), membership);
    Assert.assertFalse("The membership update should succeed", membershipResult.isError());

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assert.assertFalse("Group membership alone should confer nothing before the grant",
        user2Permissions.userHasReadAccessToResource(folder.getResourceId()));

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(group.getId()), FilesystemResourcePermission.WRITE));
    updatePermissionsAsUser1(folder, request);

    Assert.assertTrue("A WRITE grant to the group should give the member write access",
        user2Permissions.userHasWriteAccessToResource(folder.getResourceId()));
    Assert.assertTrue("Write access through the group should imply read access",
        user2Permissions.userHasReadAccessToResource(folder.getResourceId()));
    Assert.assertFalse("The group grant should not make the member the owner",
        user2Permissions.userIsOwnerOfResource(folder.getResourceId()));
  }

  @Test
  public void folderLifecycleRunsThroughTheCypherLayer() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder parent = createFolderUnderUser1Home("Lifecycle Parent");
    FolderServerFolder child = createFolderUnder(parent.getResourceId(), "Lifecycle Child");

    // Find by id
    FolderServerFolder foundById = user1Folders.findFolderById(child.getResourceId());
    Assert.assertNotNull("The nested folder should be found by id", foundById);
    Assert.assertEquals("The folder found by id should carry the created name",
        "Lifecycle Child", foundById.getName());

    // Find through the parent listing
    FileSystemResource foundInParent =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child");
    Assert.assertNotNull("The nested folder should be found under its parent by name", foundInParent);
    Assert.assertEquals("The lookup under the parent should return the same node",
        child.getId(), foundInParent.getId());

    // Rename; the graph stores the lowercased name alongside, for case-insensitive ordering
    Map<NodeProperty, String> updateFields = new HashMap<>();
    updateFields.put(NodeProperty.NAME, "Lifecycle Child Renamed");
    updateFields.put(NodeProperty.NAME_LOWER, "lifecycle child renamed");
    FolderServerFolder renamed = user1Folders.updateFolderById(child.getResourceId(), updateFields);
    Assert.assertNotNull("The rename should return the updated folder", renamed);
    Assert.assertEquals("The rename should persist the new name", "Lifecycle Child Renamed", renamed.getName());
    Assert.assertEquals("The rename should be visible on a fresh read",
        "Lifecycle Child Renamed", user1Folders.findFolderById(child.getResourceId()).getName());

    // Delete, leaf first
    Assert.assertTrue("Deleting the nested folder should succeed",
        user1Folders.deleteFolderById(child.getResourceId()));
    Assert.assertNull("The deleted folder should no longer be found by id",
        user1Folders.findFolderById(child.getResourceId()));
    Assert.assertNull("The deleted folder should no longer appear under its parent",
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child Renamed"));

    Assert.assertTrue("Deleting the emptied parent should succeed",
        user1Folders.deleteFolderById(parent.getResourceId()));
    Assert.assertNull("The deleted parent should no longer be found by id",
        user1Folders.findFolderById(parent.getResourceId()));
  }

}
