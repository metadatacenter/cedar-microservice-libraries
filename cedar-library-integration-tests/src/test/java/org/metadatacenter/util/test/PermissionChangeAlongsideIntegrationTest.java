package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.GraphWrite;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.Map;
import java.util.UUID;

/**
 * A permission change and a write added to it commit together or not at all. The search-permission
 * event a change requires is such a write: recorded afterwards in a transaction of its own, it could
 * fail and leave the index on the old keys with nothing left to correct them.
 */
@Tag("integration")
public class PermissionChangeAlongsideIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarFolderId user1Home;
  private static String boltUri;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user1Home = CedarDataServices.getInstance().getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();
    boltUri = "bolt://" + CedarEnvironmentSource.get("CEDAR_NEO4J_HOST") + ":"
        + CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");
  }

  @Test
  public void anAddedWriteCommitsWithThePermissionChange() throws Exception {
    FolderServerFolder folder = folder("Alongside committed");
    String marker = UUID.randomUUID().toString();

    var result = permissions().updateResourcePermissions(folder.getResourceId(), grantToUser2(ResourceRole.VIEWER),
        RevisionPrecondition.any(), probe(marker));

    Assertions.assertFalse(result.isError(), String.valueOf(result));
    Assertions.assertEquals(1, probes(marker));
    Assertions.assertEquals(ResourceRole.VIEWER, roleOfUser2(folder));
  }

  @Test
  public void aFailedAddedWriteRollsThePermissionChangeBack() throws Exception {
    FolderServerFolder folder = folder("Alongside rolled back");
    permissions().updateResourcePermissions(folder.getResourceId(), grantToUser2(ResourceRole.VIEWER));
    String marker = UUID.randomUUID().toString();
    GraphWrite failing = transaction -> {
      probe(marker).writeIn(transaction);
      throw new IllegalStateException("the event could not be recorded");
    };

    Assertions.assertThrows(RuntimeException.class, () -> permissions().updateResourcePermissions(
        folder.getResourceId(), grantToUser2(ResourceRole.EDITOR), RevisionPrecondition.any(), failing));

    Assertions.assertEquals(0, probes(marker), "the added write is rolled back");
    Assertions.assertEquals(ResourceRole.VIEWER, roleOfUser2(folder), "the permission change is rolled back with it");
  }

  @Test
  public void aFailedAddedWriteRollsAnOwnershipTransferBack() throws Exception {
    FolderServerFolder folder = folder("Alongside transfer rolled back");
    long revision = permissions().getVersionedResourcePermissions(folder.getResourceId()).revision();

    Assertions.assertThrows(RuntimeException.class, () -> permissions().transferResourceOwnership(
        folder.getResourceId(), user2.getResourceId(), RevisionPrecondition.exact(revision),
        transaction -> {
          throw new IllegalStateException("the event could not be recorded");
        }));

    Assertions.assertTrue(permissions().userIsOwnerOfResource(folder.getResourceId()),
        "the transfer is rolled back with the failed write");
  }

  private static ResourcePermissionServiceSession permissions() {
    return CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context);
  }

  private static ResourcePermissionsRequest grantToUser2(ResourceRole role) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(new ResourcePermissionUser(user2.getId()), role));
    return request;
  }

  private static ResourceRole roleOfUser2(FolderServerFolder folder) throws Exception {
    return CedarDataServices.getInstance()
        .getResourcePermissionServiceSession(CedarRequestContextFactory.fromUser(user2))
        .getResourceAuthority(folder.getResourceId()).highestSatisfiedRole();
  }

  private static GraphWrite probe(String marker) {
    return transaction -> transaction.run("CREATE (:AlongsideProbe {marker: $marker})", Map.of("marker", marker)).consume();
  }

  private static long probes(String marker) {
    try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.none()); Session session = driver.session()) {
      return session.run("MATCH (p:AlongsideProbe {marker: $marker}) RETURN count(p) AS c", Map.of("marker", marker))
          .single().get("c").asLong();
    }
  }

  private static FolderServerFolder folder(String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Created by PermissionChangeAlongsideIntegrationTest");
    return CedarDataServices.getInstance().getFolderServiceSession(user1Context).createFolderAsChildOfId(folder, user1Home,
        cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class));
  }
}
