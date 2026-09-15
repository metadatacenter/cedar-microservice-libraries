package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Map;

/**
 * What {@code getResourceMaterializedPermission} answers, against a real graph.
 *
 * <p>This is the value that becomes the {@code users} and {@code groups} fields of every search index
 * document, so it decides who can find what. It had no test of its own: the callers are covered, and
 * the resolution itself was reached only through them.
 *
 * <p>It is worth its own test because of how it is computed. The roles are resolved by six separate
 * upward traversals of the same ancestor chain, one per role per principal kind, and the viewer pair
 * is skipped entirely when Everybody already grants read. Those are exactly the details that a
 * consolidation of those queries into fewer round trips has to preserve, and none of them is visible
 * from the callers. The area has already produced one silent defect of this shape — a grantee who was
 * not also an owner was dropped from the materialized list, so a shared artifact never carried its
 * grantee's key and a name search could not find it.
 *
 * <p>Running against embedded Neo4j is the point: these are Cypher traversals, and an assertion on a
 * mocked session would not notice a clause that matches nothing, matches too much, or fails to parse.
 */
public class MaterializedPermissionIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarFolderId user1HomeId;

  /** Owned by user 1, no grant to anyone else. */
  private static FolderServerArtifact ownedOnly;
  /** Owned by user 1, with user 2 granted VIEWER directly on it. */
  private static FolderServerArtifact sharedViewer;
  /** Owned by user 1, with user 2 granted EDITOR directly on it. */
  private static FolderServerArtifact sharedEditor;
  /** A folder user 2 may view, and an artifact inside it carrying no grant of its own. */
  private static FolderServerFolder sharedFolder;
  private static FolderServerArtifact inheritsFromFolder;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));

    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user1HomeId = CedarDataServices.getInstance().getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();

    ownedOnly = createTemplate("MP owned only", user1HomeId);
    sharedViewer = createTemplate("MP shared viewer", user1HomeId);
    sharedEditor = createTemplate("MP shared editor", user1HomeId);

    sharedFolder = createFolder("MP shared folder");
    inheritsFromFolder = createTemplate("MP inherits from folder", sharedFolder.getResourceId());

    grantToUser2(sharedViewer, ResourceRole.VIEWER);
    grantToUser2(sharedEditor, ResourceRole.EDITOR);
    grantToUser2(sharedFolder, ResourceRole.VIEWER);
  }

  /**
   * The owner resolves to MANAGER. Each role query returns owners as well as grantees, and the
   * strongest of the three wins, so an owner carries the manager key rather than the viewer one.
   */
  @Test
  public void theOwnerIsAManager() {
    Assertions.assertEquals(ResourceRole.MANAGER, roleOfUser1(ownedOnly),
        "the owner should carry the strongest role, not merely the first one resolved");
  }

  /** An artifact nobody else holds a grant on names only its owner. */
  @Test
  public void anUnsharedArtifactNamesOnlyItsOwner() {
    CedarNodeMaterializedPermissions permissions = materialized(ownedOnly);
    Assertions.assertEquals(ResourceRole.MANAGER, permissions.getUserRoles().get(user1.getId()));
    Assertions.assertNull(permissions.getUserRoles().get(user2.getId()),
        "user 2 holds no grant on this artifact and must not appear in its materialized permissions");
  }

  /**
   * A direct viewer grant appears as VIEWER. This is the case the earlier defect lost: the grantee is
   * not an owner, and a resolution that binds both to the same variable drops them.
   */
  @Test
  public void aDirectViewerGrantIsMaterialized() {
    Assertions.assertEquals(ResourceRole.VIEWER, roleOfUser2(sharedViewer),
        "a grantee who is not also an owner must still reach the materialized list");
  }

  /** A direct editor grant appears as EDITOR rather than being flattened to viewer. */
  @Test
  public void aDirectEditorGrantKeepsItsRole() {
    Assertions.assertEquals(ResourceRole.EDITOR, roleOfUser2(sharedEditor),
        "the role is the strongest granted, and editor is stronger than viewer");
  }

  /**
   * A grant on a folder reaches what the folder contains. This is the traversal the group cascade
   * exists to re-run, and the reason a grant high in the tree touches so much.
   */
  @Test
  public void aGrantOnAFolderReachesTheArtifactsInside() {
    Assertions.assertEquals(ResourceRole.VIEWER, roleOfUser2(inheritsFromFolder),
        "the artifact carries no grant of its own; its viewer comes from the folder above it");
  }

  /** The owner is still a manager on an inherited artifact, alongside the inherited viewer. */
  @Test
  public void inheritanceDoesNotDisplaceTheOwner() {
    CedarNodeMaterializedPermissions permissions = materialized(inheritsFromFolder);
    Assertions.assertEquals(ResourceRole.MANAGER, permissions.getUserRoles().get(user1.getId()));
    Assertions.assertEquals(ResourceRole.VIEWER, permissions.getUserRoles().get(user2.getId()));
  }

  // ── fixtures and helpers ───────────────────────────────────────────────────

  private static CedarNodeMaterializedPermissions materialized(org.metadatacenter.model.folderserver.basic.FileSystemResource resource) {
    ResourcePermissionServiceSession session =
        CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context);
    CedarNodeMaterializedPermissions permissions = session.getResourceMaterializedPermission(resource.getResourceId());
    Assertions.assertNotNull(permissions, "the resource exists, so it has materialized permissions");
    return permissions;
  }

  private static ResourceRole roleOfUser1(org.metadatacenter.model.folderserver.basic.FileSystemResource r) {
    return materialized(r).getUserRoles().get(user1.getId());
  }

  private static ResourceRole roleOfUser2(org.metadatacenter.model.folderserver.basic.FileSystemResource r) {
    return materialized(r).getUserRoles().get(user2.getId());
  }

  private static FolderServerTemplate createTemplate(String name, CedarFolderId parent) {
    FolderServerTemplate artifact = new FolderServerTemplate();
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    artifact.setName(name);
    artifact.setDescription("Created by MaterializedPermissionIntegrationTest");
    artifact.setVersion("1.0.0");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    FolderServerArtifact created = CedarDataServices.getInstance().getFolderServiceSession(user1Context)
        .createResourceAsChildOfId(artifact, parent);
    Assertions.assertNotNull(created, "The artifact '" + name + "' should be created");
    return (FolderServerTemplate) created;
  }

  private static FolderServerFolder createFolder(String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Created by MaterializedPermissionIntegrationTest");
    CedarFolderId newFolderId =
        CedarFolderId.build(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.FOLDER));
    FolderServerFolder created = CedarDataServices.getInstance().getFolderServiceSession(user1Context)
        .createFolderAsChildOfId(folder, user1HomeId, newFolderId);
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
    return created;
  }

  private static void grantToUser2(org.metadatacenter.model.folderserver.basic.FileSystemResource resource,
                                   ResourceRole role) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), role));
    BackendCallResult result = CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(resource.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the grant should succeed: " + result);
  }
}
