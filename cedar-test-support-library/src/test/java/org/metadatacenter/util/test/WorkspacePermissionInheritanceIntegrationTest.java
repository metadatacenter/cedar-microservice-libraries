package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.SystemComponent;
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
 * Direct tests of ACL inheritance down the folder tree, against an in-process Neo4j. The
 * access-check Cypher (CypherQueryBuilderFilesystemResourcePermission) resolves permissions by
 * walking the ancestor chain before matching the permission relation:
 *
 *   (resource)&lt;-[:CONTAINS*0..]-()&lt;-[:OWNS]-(user)                                (ownership)
 *   (resource)&lt;-[:CONTAINS*0..]-()&lt;-[:CANREAD|CANWRITE]-()&lt;-[:MEMBEROF*0..1]-(user) (grants)
 *
 * So CEDAR does inherit permissions downward: a grant on a folder applies to everything the
 * folder transitively contains, with nothing materialized on the descendants. A READ check is
 * satisfied by either CANREAD or CANWRITE; a WRITE check only by CANWRITE. Grants never
 * propagate upward to ancestors.
 */
public class WorkspacePermissionInheritanceIntegrationTest {

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

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspacePermissionInheritanceIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, newFolderId);
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
    return created;
  }

  /**
   * Grants user2 the given permission on the folder, as user1. The update request replaces the
   * full permission sets; the owner must stay user1, since the validator rejects a request
   * without an owner.
   */
  private static void grantUser2(FolderServerFolder folder, FilesystemResourcePermission permission) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), permission));
    applyAsUser1(folder, request);
  }

  private static void applyAsUser1(FolderServerFolder folder, ResourcePermissionsRequest request) {
    BackendCallResult result = permissionsOf(user1Context).updateResourcePermissions(folder.getResourceId(), request);
    if (result.isError()) {
      Assertions.fail("The permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  @Test
  public void readGrantOnTopFolderReachesEveryDescendant() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Inherit Read A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Inherit Read B");
    // The leaf folder is artifact-free; nothing is attached to it besides the CONTAINS chain
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Inherit Read C");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(a.getResourceId()),
        "Before the grant, user2 should not read the top folder");
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(c.getResourceId()),
        "Before the grant, user2 should not read the leaf folder");

    grantUser2(a, FilesystemResourcePermission.READ);

    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(a.getResourceId()),
        "The READ grant should apply to the granted folder itself");
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(b.getResourceId()),
        "The READ grant should inherit down to the middle folder");
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(c.getResourceId()),
        "The READ grant should inherit down to the artifact-free leaf folder");

    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(a.getResourceId()),
        "A READ grant should never confer write on the granted folder");
    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(b.getResourceId()),
        "A READ grant should never confer write on a descendant");
    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(c.getResourceId()),
        "A READ grant should never confer write on the leaf");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(c.getResourceId()),
        "Inherited read should not make user2 the owner of a descendant");
  }

  @Test
  public void writeGrantOnMidFolderGivesWriteBelowButNotAbove() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Inherit Write A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Inherit Write B");
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Inherit Write C");

    grantUser2(a, FilesystemResourcePermission.READ);
    grantUser2(b, FilesystemResourcePermission.WRITE);

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertTrue(user2Permissions.userHasWriteAccessToResource(b.getResourceId()),
        "The WRITE grant should apply to the granted mid folder");
    Assertions.assertTrue(user2Permissions.userHasWriteAccessToResource(c.getResourceId()),
        "The WRITE grant should inherit down to the leaf");
    Assertions.assertFalse(user2Permissions.userHasWriteAccessToResource(a.getResourceId()),
        "The WRITE grant on the mid folder should not propagate up to its parent");

    // The READ check matches CANREAD or CANWRITE, so the WRITE grant also satisfies read below
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(a.getResourceId()),
        "Read on the parent should come from its own READ grant");
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(c.getResourceId()),
        "Write on the leaf should imply read on the leaf");
  }

  @Test
  public void revokingTheAncestorGrantRemovesDescendantAccess() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Revoke A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Revoke B");

    grantUser2(a, FilesystemResourcePermission.READ);
    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertTrue(user2Permissions.userHasReadAccessToResource(b.getResourceId()),
        "The grant should give user2 read on the descendant");

    // Replacing the permission sets with empty ones revokes the user grant; nothing was ever
    // written to the descendant, so access disappears with the single relation on the ancestor
    ResourcePermissionsRequest revocation = new ResourcePermissionsRequest();
    revocation.setOwner(new ResourcePermissionUser(user1.getId()));
    applyAsUser1(a, revocation);

    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(a.getResourceId()),
        "After the revocation, user2 should not read the granted folder");
    Assertions.assertFalse(user2Permissions.userHasReadAccessToResource(b.getResourceId()),
        "After the revocation, user2 should not read the descendant either");
  }

}
