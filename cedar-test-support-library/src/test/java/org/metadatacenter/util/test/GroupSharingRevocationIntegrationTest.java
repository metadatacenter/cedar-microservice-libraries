package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUserRequest;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroup;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Map;

/**
 * Whether access granted through a group actually goes away again.
 *
 * <p>Granting is fail-safe: if a grant does not take, somebody cannot see something and says so.
 * Revoking is fail-dangerous: if a revocation does not take, somebody keeps access and nobody finds
 * out. Existing tests cover the granting direction — {@code WorkspacePermissionIntegrationTest}
 * establishes that a group grant resolves through membership — but nothing covered the reverse, which
 * is the direction that matters.
 *
 * <p>Group membership is also the widest lever in the sharing model. A grant to a group applies to
 * whoever is in that group at the time of asking, so changing the membership silently changes who can
 * reach every resource ever shared with it, without touching any of those resources' ACLs and without
 * their owners being involved. That makes both directions of a membership change worth pinning.
 *
 * <p>Asserted at the graph layer, where the permission decision is actually made, because the effect
 * under test is on resource access while the cause is a group operation, and no single REST surface
 * covers both. The REST side of a membership change — who may perform it — is covered in the group
 * server's own matrix.
 */
public class GroupSharingRevocationIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    // The Redis redirection must be in place before the CedarConfig singleton is built.
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);
    user1HomeId = CedarDataServices.getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();
  }

  /**
   * Removing a member ends the access that membership conferred. The grant on the folder is untouched
   * throughout: only the group's membership changes, which is the point — access has to be recomputed
   * from current membership rather than fixed at the moment of granting.
   */
  @Test
  public void removingAMemberEndsTheAccessTheGroupConferred() {
    FolderServerFolder folder = folder("Revocation Folder");
    FolderServerGroup group = group("revocation-test-group");
    setMembers(group, true);   // user1 administers, user2 is a member
    grantGroup(folder, group, FilesystemResourcePermission.WRITE);

    Assertions.assertTrue(user2Permissions().userHasWriteAccessToResource(folder.getResourceId()),
        "a member of a group holding WRITE should have write access");

    // Only the membership changes. The folder's ACL still grants the group WRITE.
    setMembers(group, false);  // user2 removed; user1 remains administrator

    Assertions.assertFalse(user2Permissions().userHasWriteAccessToResource(folder.getResourceId()),
        "removing the member should have ended the write access the group conferred");
    Assertions.assertFalse(user2Permissions().userHasReadAccessToResource(folder.getResourceId()),
        "removing the member should have ended read access too");
  }

  /**
   * Deleting the group ends the access it conferred. The interesting part is not the answer but that
   * the answer is defined: a folder can outlive a group it was shared with, so the grant either has to
   * be cleaned up or has to stop resolving.
   */
  @Test
  public void deletingTheGroupEndsTheAccessItConferred() {
    FolderServerFolder folder = folder("Group Deletion Folder");
    FolderServerGroup group = group("deletion-test-group");
    setMembers(group, true);
    grantGroup(folder, group, FilesystemResourcePermission.WRITE);

    Assertions.assertTrue(user2Permissions().userHasWriteAccessToResource(folder.getResourceId()),
        "a member of a group holding WRITE should have write access");

    boolean deleted = CedarDataServices.getGroupServiceSession(user1Context)
        .deleteGroupById(group.getResourceId());
    Assertions.assertTrue(deleted, "the owner should be able to delete their own group");

    Assertions.assertFalse(user2Permissions().userHasWriteAccessToResource(folder.getResourceId()),
        "deleting the group should have ended the access it conferred");

    // The folder itself must survive, still owned by user 1: deleting a group someone shared a folder
    // with must not take the folder with it.
    FolderServerFolder after = CedarDataServices.getFolderServiceSession(user1Context)
        .findFolderById(folder.getResourceId());
    Assertions.assertNotNull(after, "deleting the group must not delete the folder shared with it");
    Assertions.assertTrue(user1Permissions().userIsOwnerOfResource(folder.getResourceId()),
        "the folder should still be owned by its owner after the group was deleted");
    Assertions.assertTrue(user1Permissions().userHasWriteAccessToResource(folder.getResourceId()),
        "the owner should still have write access after the group was deleted");
  }

  // ── fixtures and helpers ───────────────────────────────────────────────────

  private static FolderServerFolder folder(String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by GroupSharingRevocationIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = CedarDataServices.getFolderServiceSession(user1Context)
        .createFolderAsChildOfId(newFolder, user1HomeId, newFolderId);
    Assertions.assertNotNull(created, "the fixture folder should be created");
    return created;
  }

  private static FolderServerGroup group(String name) {
    FolderServerGroup created = CedarDataServices.getGroupServiceSession(user1Context)
        .createGroup(name, "Created by GroupSharingRevocationIntegrationTest");
    Assertions.assertNotNull(created, "the fixture group should be created");
    return created;
  }

  /**
   * Sets the group's membership. A membership update replaces the whole set, so user 1 is always
   * restated as creator and administrator; {@code includeUser2} decides whether user 2 is a member.
   */
  private static void setMembers(FolderServerGroup group, boolean includeUser2) {
    CedarGroupUsersRequest request = new CedarGroupUsersRequest();
    request.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    if (includeUser2) {
      request.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    }
    GroupServiceSession groups = CedarDataServices.getGroupServiceSession(user1Context);
    BackendCallResult result = groups.updateGroupUsers(group.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the membership update should succeed");
  }

  /** Grants the group the given permission on the folder, as the folder's owner. */
  private static void grantGroup(FolderServerFolder folder, FolderServerGroup group,
                                 FilesystemResourcePermission permission) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(group.getId()), permission));
    BackendCallResult result = CedarDataServices.getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(folder.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the group grant should succeed");
  }

  private static ResourcePermissionServiceSession user1Permissions() {
    return CedarDataServices.getResourcePermissionServiceSession(user1Context);
  }

  private static ResourcePermissionServiceSession user2Permissions() {
    return CedarDataServices.getResourcePermissionServiceSession(user2Context);
  }

}
