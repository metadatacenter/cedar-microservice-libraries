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

  @BeforeAll
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

    user1HomeId = CedarDataServices.getInstance().getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getFolderServiceSession(context);
  }

  private static ResourcePermissionServiceSession permissionsOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getResourcePermissionServiceSession(context);
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
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
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
      Assertions.fail("The permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  @Test
  public void ownerHasReadAndWriteAccessToHomeFolder() {
    ResourcePermissionServiceSession user1Permissions = permissionsOf(user1Context);
    Assertions.assertTrue(user1Permissions.userHasReadAccessToResource(user1HomeId),
        "The owner should have read access to the home folder");
    Assertions.assertTrue(user1Permissions.userHasWriteAccessToResource(user1HomeId),
        "The owner should have write access to the home folder");
    Assertions.assertTrue(user1Permissions.userIsOwnerOfResource(user1HomeId),
        "The home folder should report user1 as its owner");
  }

  @Test
  public void strangerHasNoAccessToHomeFolder() {
    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(user1HomeId),
        "A stranger should have no read access to another user's home folder");
    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(user1HomeId),
        "A stranger should have no write access to another user's home folder");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(user1HomeId),
        "A stranger should not be reported as the owner of another user's home folder");
  }

  @Test
  public void directUserGrantGivesReadButNotWrite() {
    FolderServerFolder folder = createFolderUnderUser1Home("Direct Grant Folder");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(folder.getResourceId()),
        "Before the grant, user2 should have no read access");

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), FilesystemResourcePermission.READ));
    updatePermissionsAsUser1(folder, request);

    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(folder.getResourceId()),
        "After a READ grant, user2 should have read access");
    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(folder.getResourceId()),
        "A READ grant should not confer write access");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(folder.getResourceId()),
        "A READ grant should not confer ownership");
  }

  @Test
  public void groupWriteGrantResolvesThroughMembership() {
    FolderServerFolder folder = createFolderUnderUser1Home("Group Grant Folder");

    GroupServiceSession user1Groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = user1Groups.createGroup("workspace-integration-test-group",
        "Group for the group-resolved ACL test");
    Assertions.assertNotNull(group, "The group should be created");
    Assertions.assertTrue(user1Groups.userAdministersGroup(group.getResourceId()),
        "The creator should administer the new group");

    // Membership updates replace the full member and administrator sets, so the request lists
    // user1 (creator, administrator) alongside the new member user2
    CedarGroupUsersRequest membership = new CedarGroupUsersRequest();
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    BackendCallResult membershipResult = user1Groups.updateGroupUsers(group.getResourceId(), membership);
    Assertions.assertFalse(membershipResult.isError(), "The membership update should succeed");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(folder.getResourceId()),
        "Group membership alone should confer nothing before the grant");

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(group.getId()), FilesystemResourcePermission.WRITE));
    updatePermissionsAsUser1(folder, request);

    Assertions.assertTrue(user2Permissions.userHasWriteAccessToResource(folder.getResourceId()),
        "A WRITE grant to the group should give the member write access");
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(folder.getResourceId()),
        "Write access through the group should imply read access");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(folder.getResourceId()),
        "The group grant should not make the member the owner");
  }

  @Test
  public void folderLifecycleRunsThroughTheCypherLayer() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder parent = createFolderUnderUser1Home("Lifecycle Parent");
    FolderServerFolder child = createFolderUnder(parent.getResourceId(), "Lifecycle Child");

    // Find by id
    FolderServerFolder foundById = user1Folders.findFolderById(child.getResourceId());
    Assertions.assertNotNull(foundById, "The nested folder should be found by id");
    Assertions.assertEquals("Lifecycle Child", foundById.getName(),
        "The folder found by id should carry the created name");

    // Find through the parent listing
    FileSystemResource foundInParent =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child");
    Assertions.assertNotNull(foundInParent, "The nested folder should be found under its parent by name");
    Assertions.assertEquals(child.getId(), foundInParent.getId(),
        "The lookup under the parent should return the same node");

    // Rename; the graph stores the lowercased name alongside, for case-insensitive ordering
    Map<NodeProperty, String> updateFields = new HashMap<>();
    updateFields.put(NodeProperty.NAME, "Lifecycle Child Renamed");
    updateFields.put(NodeProperty.NAME_LOWER, "lifecycle child renamed");
    FolderServerFolder renamed = user1Folders.updateFolderById(child.getResourceId(), updateFields);
    Assertions.assertNotNull(renamed, "The rename should return the updated folder");
    Assertions.assertEquals("Lifecycle Child Renamed", renamed.getName(), "The rename should persist the new name");
    Assertions.assertEquals("Lifecycle Child Renamed", user1Folders.findFolderById(child.getResourceId()).getName(),
        "The rename should be visible on a fresh read");

    // Delete, leaf first
    Assertions.assertTrue(user1Folders.deleteFolderById(child.getResourceId()),
        "Deleting the nested folder should succeed");
    Assertions.assertNull(user1Folders.findFolderById(child.getResourceId()),
        "The deleted folder should no longer be found by id");
    Assertions.assertNull(
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child Renamed"),
        "The deleted folder should no longer appear under its parent");

    Assertions.assertTrue(user1Folders.deleteFolderById(parent.getResourceId()),
        "Deleting the emptied parent should succeed");
    Assertions.assertNull(user1Folders.findFolderById(parent.getResourceId()),
        "The deleted parent should no longer be found by id");
  }

  @Test
  public void deletingANonEmptyFolderPreservesItsWholeSubtree() {
    FolderServiceSession user1Folders = foldersOf(user1Context);
    FolderServerFolder parent = createFolderUnderUser1Home("Protected Nonempty Parent");
    FolderServerFolder child = createFolderUnder(parent.getResourceId(), "Protected Child");

    Assertions.assertFalse(user1Folders.deleteFolderById(parent.getResourceId()),
        "Deleting a non-empty folder should be refused atomically");
    Assertions.assertNotNull(user1Folders.findFolderById(parent.getResourceId()),
        "The refused deletion must preserve the parent");
    Assertions.assertNotNull(user1Folders.findFolderById(child.getResourceId()),
        "The refused deletion must preserve the child");
    Assertions.assertNotNull(user1Folders.findFilesystemResourceByParentFolderIdAndName(
            parent.getResourceId(), child.getName()),
        "The refused deletion must preserve the parent-child relationship");
  }

}
