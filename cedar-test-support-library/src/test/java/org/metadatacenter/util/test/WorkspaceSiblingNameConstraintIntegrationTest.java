package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.SiblingNameConflictException;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The sibling-name constraints and the conflict they raise, against an in-process Neo4j. Provisioning
 * adds two composite uniqueness constraints, one over a folder's parent and lowercased name and one
 * over a category's. The workspace proxies turn a violation of either into a
 * SiblingNameConflictException, which the servers answer with 409. The proxies recognize the violation
 * by the constrained property names in Neo4j's error message, so a Neo4j upgrade that changed the
 * message would turn every such 409 into a 500, and this class would fail first.
 *
 * <p>The constraints would outlive this class and refuse other classes' fixtures, so the class runs on
 * a graph of its own. It discards the shared graph before it starts and again when it finishes,
 * seeding a fresh one each time.
 */
@Tag("integration")
public class WorkspaceSiblingNameConstraintIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarRequestContext user1Context;
  private static CedarFolderId user1HomeId;
  private static CedarCategoryId rootCategoryId;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.stopAndReset();
    startSeededGraph();
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));

    // The constraints provisioning creates, after the backfill it runs first
    AdminServiceSession admin = CedarDataServices.getInstance().getAdminServiceSession(
        CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig)));
    Assertions.assertTrue(admin.backfillFolderParentIds());
    Assertions.assertTrue(admin.createUniqueConstraint(NodeLabel.RESOURCE, NodeProperty.ID));
    Assertions.assertTrue(admin.createUniqueConstraint(NodeLabel.FOLDER,
        List.of(NodeProperty.PARENT_FOLDER_ID, NodeProperty.NAME_LOWER)));
    Assertions.assertTrue(admin.createUniqueConstraint(NodeLabel.CATEGORY,
        List.of(NodeProperty.PARENT_CATEGORY_ID, NodeProperty.NAME_LOWER)));

    user1Context = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
    user1HomeId = foldersOf(user1Context).findHomeFolderOf().getResourceId();
    rootCategoryId = categoriesOf(user1Context).getRootCategory().getResourceId();
  }

  @AfterAll
  public static void restoreTheSharedGraph() {
    EmbeddedCedarNeo4j.stopAndReset();
    startSeededGraph();
  }

  private static void startSeededGraph() {
    // The Redis redirection must be in place before startRedirectAndSeed builds the CedarConfig
    // singleton from the environment
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getFolderServiceSession(context);
  }

  private static CategoryServiceSession categoriesOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getCategoryServiceSession(context);
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    return createFolderUnder(parentId, name, cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class));
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name, CedarFolderId id) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspaceSiblingNameConstraintIntegrationTest");
    return foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, id);
  }

  private static FolderServerCategory createCategoryUnder(CedarCategoryId parentId, String name) {
    return categoriesOf(user1Context).createCategory(parentId, name,
        "Created by WorkspaceSiblingNameConstraintIntegrationTest", null);
  }

  /** The fields a rename writes: the name, and its lowercase form the constraint is over. */
  private static Map<NodeProperty, String> rename(String name) {
    return Map.of(NodeProperty.NAME, name, NodeProperty.NAME_LOWER, name.toLowerCase(Locale.ROOT));
  }

  /**
   * Two folders under one parent may not share a name in any case, whether the second arrives by
   * creation, by rename or by a move. The same name under another parent is allowed, and a refused
   * write leaves the tree as it was.
   */
  @Test
  public void aFolderNameIsUniqueAmongItsSiblingsInAnyCase() {
    FolderServiceSession folders = foldersOf(user1Context);
    FolderServerFolder parent = createFolderUnder(user1HomeId, "Sibling Parent");
    FolderServerFolder otherParent = createFolderUnder(user1HomeId, "Sibling Other Parent");
    FolderServerFolder report = createFolderUnder(parent.getResourceId(), "Report");
    FolderServerFolder notes = createFolderUnder(parent.getResourceId(), "Notes");

    Assertions.assertThrows(SiblingNameConflictException.class,
        () -> createFolderUnder(parent.getResourceId(), "REPORT"));
    FolderServerFolder elsewhere = createFolderUnder(otherParent.getResourceId(), "report");
    Assertions.assertNotNull(elsewhere, "the same name under another parent is allowed");

    Assertions.assertThrows(SiblingNameConflictException.class,
        () -> folders.updateFolderById(notes.getResourceId(), rename("report"), RevisionPrecondition.any()));
    Assertions.assertEquals("Notes", folders.findFolderById(notes.getResourceId()).getName());

    Assertions.assertThrows(SiblingNameConflictException.class,
        () -> folders.moveFolder(elsewhere.getResourceId(), parent.getResourceId()));
    Assertions.assertEquals(elsewhere.getId(), folders.findFilesystemResourceByParentFolderIdAndName(
        otherParent.getResourceId(), "report").getId(), "a refused move must leave the folder where it was");
    Assertions.assertEquals(report.getId(), folders.findFilesystemResourceByParentFolderIdAndName(
        parent.getResourceId(), "report").getId());
  }

  /** Categories follow the same rule among the children of one parent category. */
  @Test
  public void aCategoryNameIsUniqueAmongItsSiblingsInAnyCase() {
    CategoryServiceSession categories = categoriesOf(user1Context);
    FolderServerCategory parent = createCategoryUnder(rootCategoryId, "Sibling Category Parent");
    Assertions.assertNotNull(createCategoryUnder(parent.getResourceId(), "Vocabulary"));
    FolderServerCategory glossary = createCategoryUnder(parent.getResourceId(), "Glossary");

    Assertions.assertThrows(SiblingNameConflictException.class,
        () -> createCategoryUnder(parent.getResourceId(), "VOCABULARY"));
    Assertions.assertNotNull(createCategoryUnder(rootCategoryId, "Vocabulary"),
        "the same name under another parent is allowed");

    Assertions.assertThrows(SiblingNameConflictException.class,
        () -> categories.updateCategoryById(glossary.getResourceId(), rename("vocabulary"), RevisionPrecondition.any()));
    Assertions.assertEquals("Glossary", categories.getCategoryById(glossary.getResourceId()).getName());
  }

  /**
   * Only a sibling-name violation is a conflict the caller can resolve by choosing another name. A
   * violation of any other constraint, here a second resource under an identifier already taken,
   * stays a server error, so a user is never told that a name is taken when it is not.
   */
  @Test
  public void anyOtherConstraintViolationRemainsAServerError() {
    FolderServerFolder holder = createFolderUnder(user1HomeId, "Identifier Holder");

    RuntimeException error = Assertions.assertThrows(RuntimeException.class,
        () -> createFolderUnder(user1HomeId, "Identifier Reuser", holder.getResourceId()));

    Assertions.assertFalse(error instanceof SiblingNameConflictException, error.toString());
    Assertions.assertNull(foldersOf(user1Context).findFilesystemResourceByParentFolderIdAndName(
        user1HomeId, "identifier reuser"));
    Assertions.assertEquals("Identifier Holder",
        foldersOf(user1Context).findFolderById(holder.getResourceId()).getName());
  }
}
