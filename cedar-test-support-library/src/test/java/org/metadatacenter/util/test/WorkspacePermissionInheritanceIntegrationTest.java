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
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Map;

/** Direct tests of role inheritance and precedence against an in-process Neo4j. */
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
   * full direct-grant sets and explicitly restates the unchanged owner.
   */
  private static void grantUser2(FolderServerFolder folder, ResourceRole role) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), role));
    applyAsUser1(folder, request);
  }

  private static void applyAsUser1(FolderServerFolder folder, ResourcePermissionsRequest request) {
    BackendCallResult result = permissionsOf(user1Context).updateResourcePermissions(folder.getResourceId(), request);
    if (result.isError()) {
      Assertions.fail("The permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  @Test
  public void viewerGrantOnTopFolderReachesEveryDescendant() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Inherit Read A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Inherit Read B");
    // The leaf folder is artifact-free; nothing is attached to it besides the CONTAINS chain
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Inherit Read C");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.VIEWER),
        "Before the grant, user2 should not read the top folder");
    Assertions.assertFalse(user2Permissions.userHasRole(c.getResourceId(), ResourceRole.VIEWER),
        "Before the grant, user2 should not read the leaf folder");

    grantUser2(a, ResourceRole.VIEWER);

    Assertions.assertTrue(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.VIEWER),
        "The READ grant should apply to the granted folder itself");
    Assertions.assertTrue(user2Permissions.userHasRole(b.getResourceId(), ResourceRole.VIEWER),
        "The READ grant should inherit down to the middle folder");
    Assertions.assertTrue(user2Permissions.userHasRole(c.getResourceId(), ResourceRole.VIEWER),
        "The READ grant should inherit down to the artifact-free leaf folder");

    Assertions.assertFalse(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.EDITOR),
        "A READ grant should never confer write on the granted folder");
    Assertions.assertFalse(user2Permissions.userHasRole(b.getResourceId(), ResourceRole.EDITOR),
        "A READ grant should never confer write on a descendant");
    Assertions.assertFalse(user2Permissions.userHasRole(c.getResourceId(), ResourceRole.EDITOR),
        "A READ grant should never confer write on the leaf");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(c.getResourceId()),
        "Inherited read should not make user2 the owner of a descendant");
  }

  @Test
  public void nestedGrantsResolveToTheStrongestRoleWithoutPropagatingUpward() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Role Precedence A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Role Precedence B");
    FolderServerFolder c = createFolderUnder(b.getResourceId(), "Role Precedence C");

    grantUser2(a, ResourceRole.VIEWER);
    grantUser2(b, ResourceRole.EDITOR);
    grantUser2(c, ResourceRole.MANAGER);

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertEquals(ResourceRole.VIEWER,
        user2Permissions.getResourceAuthority(a.getResourceId()).highestSatisfiedRole());
    Assertions.assertEquals(ResourceRole.EDITOR,
        user2Permissions.getResourceAuthority(b.getResourceId()).highestSatisfiedRole());
    Assertions.assertEquals(ResourceRole.MANAGER,
        user2Permissions.getResourceAuthority(c.getResourceId()).highestSatisfiedRole());

    Assertions.assertFalse(user2Permissions.userHasRole(b.getResourceId(), ResourceRole.MANAGER),
        "The Manager grant on the leaf must not propagate upward");
    Assertions.assertTrue(user2Permissions.userHasRole(c.getResourceId(), ResourceRole.MANAGER),
        "The direct Manager grant should win over inherited Viewer and Editor grants");
    Assertions.assertFalse(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.EDITOR),
        "The Editor grant on the middle folder must not propagate upward");
    Assertions.assertTrue(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.VIEWER),
        "The parent should retain its Viewer role");
    Assertions.assertTrue(user2Permissions.userHasRole(c.getResourceId(), ResourceRole.VIEWER),
        "Manager should satisfy a Viewer requirement");
  }

  @Test
  public void revokingTheAncestorGrantRemovesDescendantAccess() {
    FolderServerFolder a = createFolderUnder(user1HomeId, "Revoke A");
    FolderServerFolder b = createFolderUnder(a.getResourceId(), "Revoke B");

    grantUser2(a, ResourceRole.VIEWER);
    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertTrue(user2Permissions.userHasRole(b.getResourceId(), ResourceRole.VIEWER),
        "The grant should give user2 read on the descendant");

    // Replacing the permission sets with empty ones revokes the user grant; nothing was ever
    // written to the descendant, so access disappears with the single relation on the ancestor
    ResourcePermissionsRequest revocation = new ResourcePermissionsRequest();
    revocation.setOwner(new ResourcePermissionUser(user1.getId()));
    applyAsUser1(a, revocation);

    Assertions.assertFalse(user2Permissions.userHasRole(a.getResourceId(), ResourceRole.VIEWER),
        "After the revocation, user2 should not read the granted folder");
    Assertions.assertFalse(user2Permissions.userHasRole(b.getResourceId(), ResourceRole.VIEWER),
        "After the revocation, user2 should not read the descendant either");
  }

}
