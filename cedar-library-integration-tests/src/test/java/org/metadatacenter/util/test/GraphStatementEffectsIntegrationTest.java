package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

/**
 * Effects of graph statements that no other integration test observed, executed against the embedded
 * graph instead of read from the statement's text.
 */
@Tag("integration")
public class GraphStatementEffectsIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarRequestContext user1Context;
  private static CedarFolderId user1Home;
  private static String boltUri;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    user1Context = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
    user1Home = folders().findHomeFolderOf().getResourceId();
    boltUri = "bolt://" + CedarEnvironmentSource.get("CEDAR_NEO4J_HOST") + ":"
        + CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");
  }

  /**
   * A resource that is made not open carries the flag as false. A node with no flag is neither open nor
   * closed, and the action policy offers no OpenView change for it.
   */
  @Test
  public void aResourceMadeNotOpenCarriesTheFlagAsFalse() {
    FolderServerArtifact template = template("Statement effects not open");
    CedarArtifactId templateId = CedarArtifactId.build(template.getId(), CedarResourceType.TEMPLATE);
    FolderServerFolder folder = folder("Statement effects not open folder");

    Assertions.assertTrue(folders().setOpen(templateId));
    Assertions.assertTrue(folders().setNotOpen(templateId));
    Assertions.assertTrue(folders().setOpen(folder.getResourceId()));
    Assertions.assertTrue(folders().setNotOpen(folder.getResourceId()));

    for (String id : new String[]{template.getId(), folder.getId()}) {
      Value flag = isOpen(id);
      Assertions.assertFalse(flag.isNull(), id + " lost its open flag");
      Assertions.assertFalse(flag.asBoolean(), id + " is still open");
    }
  }

  /**
   * Adding a user to Everybody changes the group's membership, so it advances the membership revision
   * that a conditional membership replacement is checked against.
   */
  @Test
  public void joiningEverybodyAdvancesItsMembershipRevision() throws Exception {
    CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));
    GroupServiceSession groups = CedarDataServices.getInstance().getGroupServiceSession(adminContext);
    FolderServerGroup everybody = groups.findGroupByName(cedarConfig.getFolderStructureConfig().getEverybodyGroup().getName());
    long before = groups.findVersionedGroupUsers(everybody.getResourceId()).revision();
    CedarUser newcomer = new CedarUser();
    newcomer.setId("https://metadatacenter.org/users/" + UUID.randomUUID());
    newcomer.setFirstName("Statement");
    newcomer.setLastName("Effects");
    newcomer.setEmail("statement-effects-" + UUID.randomUUID() + "@test.com");
    newcomer.setApiKeys(new ArrayList<>());
    newcomer.setRoles(new ArrayList<>());
    newcomer.setPermissions(new ArrayList<>());
    CedarDataServices.getInstance().getNeoUserService().createUser(newcomer);

    Assertions.assertTrue(CedarDataServices.getInstance().getUserServiceSession(adminContext)
        .addUserToEverybodyGroup(CedarUserId.build(newcomer.getId())));

    Assertions.assertEquals(before + 1, groups.findVersionedGroupUsers(everybody.getResourceId()).revision());
  }

  private static FolderServiceSession folders() {
    return CedarDataServices.getInstance().getFolderServiceSession(user1Context);
  }

  private static Value isOpen(String id) {
    try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.none()); Session session = driver.session()) {
      return session.run("MATCH (n {_id: $id}) RETURN n.isOpen AS open", Map.of("id", id)).single().get("open");
    }
  }

  private static FolderServerArtifact template(String name) {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName(name);
    template.setDescription("Created by GraphStatementEffectsIntegrationTest");
    template.setVersion("0.0.1");
    template.setPublicationStatus("bibo:draft");
    template.setLatestVersion(true);
    template.setLatestDraftVersion(true);
    template.setLatestPublishedVersion(false);
    return folders().createResourceAsChildOfId(template, user1Home);
  }

  private static FolderServerFolder folder(String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Created by GraphStatementEffectsIntegrationTest");
    return folders().createFolderAsChildOfId(folder, user1Home,
        cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class));
  }
}
