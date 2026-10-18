package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUserRequest;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.Map;

/**
 * Grants stored under the canonical VIEWER_ROLE and MANAGER_ROLE labels, read through the
 * production permission service.
 *
 * <p>Production writes viewer and manager grants as CANREAD and CANWRITE. The queries that read
 * grants also accept VIEWER_ROLE and MANAGER_ROLE, so that a later data migration can rename the
 * stored grants without changing who may do what. No writer creates the canonical labels yet, so
 * each fixture writes its grant with raw Cypher. Each test then asks the service for the role the
 * grantee holds, from which every authorization check derives, and for the materialized permissions
 * that become the {@code users} and {@code groups} fields of a search document.
 */
@Tag("integration")
public class CanonicalGrantLabelIntegrationTest {

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

  @Test
  public void aDirectViewerRoleGrantIsAViewer() {
    FolderServerArtifact artifact = createTemplate("Canonical direct viewer");
    grantWithLabel(user2.getId(), "VIEWER_ROLE", artifact);

    assertUser2Holds(ResourceRole.VIEWER, artifact);
  }

  @Test
  public void aDirectManagerRoleGrantIsAManager() {
    FolderServerArtifact artifact = createTemplate("Canonical direct manager");
    grantWithLabel(user2.getId(), "MANAGER_ROLE", artifact);

    assertUser2Holds(ResourceRole.MANAGER, artifact);
  }

  @Test
  public void aGroupViewerRoleGrantMakesItsMemberAViewer() {
    FolderServerArtifact artifact = createTemplate("Canonical group viewer");
    FolderServerGroup group = groupWithUser2("canonical-viewer-group");
    grantWithLabel(group.getId(), "VIEWER_ROLE", artifact);

    assertUser2Holds(ResourceRole.VIEWER, artifact);
    Assertions.assertEquals(ResourceRole.VIEWER, materialized(artifact).getGroupRoles().get(group.getId()));
  }

  @Test
  public void aGroupManagerRoleGrantMakesItsMemberAManager() {
    FolderServerArtifact artifact = createTemplate("Canonical group manager");
    FolderServerGroup group = groupWithUser2("canonical-manager-group");
    grantWithLabel(group.getId(), "MANAGER_ROLE", artifact);

    assertUser2Holds(ResourceRole.MANAGER, artifact);
    Assertions.assertEquals(ResourceRole.MANAGER, materialized(artifact).getGroupRoles().get(group.getId()));
  }

  // ── fixtures and helpers ───────────────────────────────────────────────────

  private static void assertUser2Holds(ResourceRole role, FolderServerArtifact artifact) {
    ResourceAuthority authority = permissionsOf(user2Context).getResourceAuthority(artifact.getResourceId());
    Assertions.assertFalse(authority.owner());
    Assertions.assertEquals(role, authority.role(), "the role check");

    CedarNodeMaterializedPermissions permissions = materialized(artifact);
    Assertions.assertEquals(role, permissions.getUserRoles().get(user2.getId()), "the materialized role");
    Assertions.assertEquals(ResourceRole.MANAGER, permissions.getUserRoles().get(user1.getId()),
        "the owner keeps its manager entry beside the grantee");
  }

  private static CedarNodeMaterializedPermissions materialized(FolderServerArtifact artifact) {
    CedarNodeMaterializedPermissions permissions =
        permissionsOf(user1Context).getResourceMaterializedPermission(artifact.getResourceId());
    Assertions.assertNotNull(permissions, "the resource exists, so it has materialized permissions");
    return permissions;
  }

  private static ResourcePermissionServiceSession permissionsOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getResourcePermissionServiceSession(context);
  }

  /** Writes the grant edge directly, as the planned migration would. */
  private static void grantWithLabel(String principalId, String label, FolderServerArtifact artifact) {
    String uri = cedarConfig.getNeo4jConfig().getBolt().getUri();
    try (Driver driver = GraphDatabase.driver(uri, AuthTokens.none()); Session session = driver.session()) {
      int created = session.run("MATCH (principal {_id: $principal}) WHERE principal:User OR principal:Group "
              + "MATCH (resource:FileSystemResource {_id: $resource}) "
              + "CREATE (principal)-[grant:" + label + "]->(resource) RETURN count(grant) AS created",
          Map.of("principal", principalId, "resource", artifact.getId())).single().get("created").asInt();
      Assertions.assertEquals(1, created, "the fixture grant should be written");
    }
  }

  private static FolderServerTemplate createTemplate(String name) {
    FolderServerTemplate artifact = new FolderServerTemplate();
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    artifact.setName(name);
    artifact.setDescription("Created by CanonicalGrantLabelIntegrationTest");
    artifact.setVersion("1.0.0");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    FolderServerArtifact created = CedarDataServices.getInstance().getFolderServiceSession(user1Context)
        .createResourceAsChildOfId(artifact, user1HomeId);
    Assertions.assertNotNull(created, "The artifact '" + name + "' should be created");
    return (FolderServerTemplate) created;
  }

  /** A group user 1 administers and user 2 belongs to. */
  private static FolderServerGroup groupWithUser2(String name) {
    var groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = groups.createGroup(name, "Created by CanonicalGrantLabelIntegrationTest");
    Assertions.assertNotNull(group, "the fixture group should be created");
    CedarGroupUsersRequest request = new CedarGroupUsersRequest();
    request.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    request.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    BackendCallResult result = groups.updateGroupUsers(group.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the membership update should succeed");
    return group;
  }
}
