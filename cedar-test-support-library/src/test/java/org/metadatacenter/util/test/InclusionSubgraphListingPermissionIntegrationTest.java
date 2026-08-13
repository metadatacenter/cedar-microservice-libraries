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
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;
import java.util.Map;

/**
 * Who the inclusion listings answer for: the artifacts that include a given one, filtered to the artifacts
 * the asking user may read.
 *
 * <p>Inclusion is a property of the artifacts and says nothing about who may see them, so the arc alone
 * matches every including artifact in the installation. These listings once returned all of them. Their
 * callers build the tree of artifacts affected by a change from the result, show that tree to the user and
 * take it as the list of artifacts to rewrite, so an unfiltered listing both discloses other people's
 * artifacts and offers them up as targets.
 *
 * <p>Running against a real Neo4j is the point. The filtering is a permission condition inside the Cypher,
 * and a string assertion on the query would not notice a clause that fails to parse, matches nothing, or
 * quietly matches everything.
 */
public class InclusionSubgraphListingPermissionIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;

  /** The included artifact every fixture below points at. Readable by both users. */
  private static FolderServerArtifact source;
  /** Includes the source; user 2 has a read grant. */
  private static FolderServerArtifact sharedTemplate;
  /** Includes the source; user 2 has no grant at all. */
  private static FolderServerArtifact privateTemplate;
  /** Includes the source, and is an element rather than a template; user 2 has no grant. */
  private static FolderServerArtifact privateElement;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));

    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);
    user1HomeId = CedarDataServices.getInstance().getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();

    source = create(new FolderServerElement(), CedarResourceType.ELEMENT, "ISL source element");
    sharedTemplate = create(new FolderServerTemplate(), CedarResourceType.TEMPLATE, "ISL shared template");
    privateTemplate = create(new FolderServerTemplate(), CedarResourceType.TEMPLATE, "ISL private template");
    privateElement = create(new FolderServerElement(), CedarResourceType.ELEMENT, "ISL private element");

    grantReadToUser2(source);
    grantReadToUser2(sharedTemplate);

    includes(sharedTemplate, source);
    includes(privateTemplate, source);
    includes(privateElement, source);
  }

  /** The owner sees everything that includes the source, which is what the listing is for. */
  @Test
  public void theOwnerSeesEveryIncludingArtifact() {
    List<String> templates = includingTemplateIdsFor(user1Context);
    Assertions.assertTrue(templates.contains(sharedTemplate.getId()), templates.toString());
    Assertions.assertTrue(templates.contains(privateTemplate.getId()), templates.toString());
    Assertions.assertEquals(List.of(privateElement.getId()), includingElementIdsFor(user1Context));
  }

  /**
   * User 2 holds a read grant on one of the two including templates. The other still includes the source
   * and still matches the arc, so its absence is the permission condition doing its work.
   */
  @Test
  public void anotherUserSeesOnlyTheIncludingArtifactsTheyMayRead() {
    Assertions.assertEquals(List.of(sharedTemplate.getId()), includingTemplateIdsFor(user2Context),
        "user 2 has a grant on one including template and none on the other");
  }

  /** The element listing is filtered on the same terms; user 2 has no grant on the only including element. */
  @Test
  public void theElementListingIsFilteredTheSameWay() {
    Assertions.assertEquals(List.of(), includingElementIdsFor(user2Context),
        "the only including element is one user 2 has no grant on");
  }

  // ── fixtures and helpers ───────────────────────────────────────────────────

  private static InclusionSubgraphServiceSession inclusionsOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getInclusionSubgraphServiceSession(context);
  }

  private static List<String> includingTemplateIdsFor(CedarRequestContext context) {
    return inclusionsOf(context).listIncludingTemplates(source.getResourceId())
        .stream().map(FolderServerTemplate::getId).sorted().toList();
  }

  private static List<String> includingElementIdsFor(CedarRequestContext context) {
    return inclusionsOf(context).listIncludingElements(source.getResourceId())
        .stream().map(FolderServerElement::getId).sorted().toList();
  }

  private static FolderServerArtifact create(FolderServerArtifact artifact, CedarResourceType type, String name) {
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(type));
    artifact.setName(name);
    artifact.setDescription("Created by InclusionSubgraphListingPermissionIntegrationTest");
    if (artifact instanceof FolderServerSchemaArtifact schema) {
      schema.setVersion("1.0.0");
      schema.setPublicationStatus("bibo:draft");
      schema.setLatestVersion(true);
      schema.setLatestDraftVersion(true);
      schema.setLatestPublishedVersion(false);
    }
    FolderServerArtifact created = CedarDataServices.getInstance().getFolderServiceSession(user1Context)
        .createResourceAsChildOfId(artifact, user1HomeId);
    Assertions.assertNotNull(created, "The artifact '" + name + "' should be created");
    return created;
  }

  private static void grantReadToUser2(FolderServerArtifact artifact) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), FilesystemResourcePermission.READ));
    BackendCallResult result = CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(artifact.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the grant should succeed");
  }

  private static void includes(FolderServerArtifact includer, FolderServerArtifact included) {
    boolean arcs = inclusionsOf(user1Context).updateInclusionArcs(includer.getResourceId(), List.of(included.getId()));
    Assertions.assertTrue(arcs, "the inclusion arc should have been created");
  }

}
