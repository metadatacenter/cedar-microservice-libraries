package org.metadatacenter.util.test;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionRequest;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUser;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUserPermissionPair;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;
import java.util.Map;

/**
 * Direct tests of the category graph and of artifact version chains, against an in-process
 * Neo4j. Two semantics pinned here differ from the folder ACL model:
 *
 * Category permissions do not inherit down the category tree. The check Cypher
 * (CypherQueryBuilderCategoryPermission) walks CONTAINS relations before the permission
 * relation, but the category tree is linked with CONTAINSCATEGORY, so only the zero-length
 * step ever matches: a grant (or ownership) is effective on exactly the granted category node.
 * The session layer also applies no gate on createCategory itself; the REST layer authorizes,
 * and the creator becomes the category's owner (OWNSCATEGORY).
 *
 * Version chains are plain graph state: a new version carries a previousVersion property and a
 * PREVIOUSVERSION relation to its predecessor, and the latest* flags are maintained explicitly
 * by the caller (the resource server flips them after creating a version); nothing recomputes
 * them.
 */
public class WorkspaceCategoryAndVersionIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;
  private static CedarCategoryId rootCategoryId;

  @BeforeClass
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

    user1HomeId = CedarDataServices.getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();

    // Seeding creates the root category the way provisioning does
    FolderServerCategory rootCategory = categoriesOf(user1Context).getRootCategory();
    Assert.assertNotNull("The seeded graph should contain the root category", rootCategory);
    rootCategoryId = rootCategory.getResourceId();
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getFolderServiceSession(context);
  }

  private static CategoryServiceSession categoriesOf(CedarRequestContext context) {
    return CedarDataServices.getCategoryServiceSession(context);
  }

  private static CategoryPermissionServiceSession categoryPermissionsOf(CedarRequestContext context) {
    return CedarDataServices.getCategoryPermissionServiceSession(context);
  }

  private static FolderServerCategory createCategoryAsUser1(CedarCategoryId parentId, String name) {
    FolderServerCategory created = categoriesOf(user1Context).createCategory(parentId, name,
        "Created by WorkspaceCategoryAndVersionIntegrationTest", null);
    Assert.assertNotNull("The category '" + name + "' should be created", created);
    return created;
  }

  /**
   * Grants user2 the given permission on the category, as user1 (the owner). The request
   * replaces the full permission sets and must restate the owner.
   */
  private static void grantUser2OnCategory(CedarCategoryId categoryId, CategoryPermission permission) {
    CategoryPermissionRequest request = new CategoryPermissionRequest();
    request.setOwner(new CategoryPermissionUser(user1.getId()));
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(user2.getId()), permission));
    BackendCallResult result = categoryPermissionsOf(user1Context).updateCategoryPermissions(categoryId, request);
    if (result.isError()) {
      Assert.fail("The category permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  private static FolderServerTemplate newTemplate(String name, String version) {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName(name);
    template.setDescription("Created by WorkspaceCategoryAndVersionIntegrationTest");
    template.setVersion(version);
    template.setPublicationStatus("bibo:draft");
    template.setLatestVersion(true);
    template.setLatestDraftVersion(true);
    template.setLatestPublishedVersion(false);
    return template;
  }

  private static FolderServerArtifact createTemplateUnderUser1Home(FolderServerTemplate template) {
    FolderServerArtifact created = foldersOf(user1Context).createResourceAsChildOfId(template, user1HomeId);
    Assert.assertNotNull("The template '" + template.getName() + "' should be created", created);
    return created;
  }

  @Test
  public void categoryTreeIsCreatedAndTraversable() {
    FolderServerCategory child = createCategoryAsUser1(rootCategoryId, "Tree Child");
    FolderServerCategory grandchild = createCategoryAsUser1(child.getResourceId(), "Tree Grandchild");

    CategoryServiceSession user1Categories = categoriesOf(user1Context);
    FolderServerCategory foundByParentAndName = user1Categories.getCategoryByParentAndName(rootCategoryId, "Tree Child");
    Assert.assertNotNull("The child category should be found under the root by name", foundByParentAndName);
    Assert.assertEquals("The lookup under the root should return the created node",
        child.getId(), foundByParentAndName.getId());

    Assert.assertEquals("The grandchild should record its parent category",
        child.getId(), user1Categories.getCategoryById(grandchild.getResourceId()).getParentCategoryId());

    List<FolderServerCategoryExtract> path = user1Categories.getCategoryPath(grandchild.getResourceId());
    Assert.assertEquals("The grandchild's category path should span root, child and grandchild", 3, path.size());
    List<String> pathIds = path.stream().map(FolderServerCategoryExtract::getId).toList();
    Assert.assertTrue("The path should contain the root category", pathIds.contains(rootCategoryId.getId()));
    Assert.assertTrue("The path should contain the child category", pathIds.contains(child.getId()));
    Assert.assertTrue("The path should contain the grandchild category", pathIds.contains(grandchild.getId()));
  }

  @Test
  public void categoryWriteGrantIsPerNodeAndImpliesAttach() {
    FolderServerCategory parent = createCategoryAsUser1(rootCategoryId, "Perm Parent");
    FolderServerCategory child = createCategoryAsUser1(parent.getResourceId(), "Perm Child");

    CategoryPermissionServiceSession user1CategoryPermissions = categoryPermissionsOf(user1Context);
    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);

    Assert.assertTrue("The creator should own the category", user1CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()));
    Assert.assertTrue("Ownership should confer write on the owned node",
        user1CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()));
    Assert.assertFalse("A stranger should not write the category before any grant",
        user2CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()));
    Assert.assertFalse("A stranger should not attach to the category before any grant",
        user2CategoryPermissions.userHasAttachAccessToCategory(parent.getResourceId()));

    grantUser2OnCategory(parent.getResourceId(), CategoryPermission.WRITE);

    Assert.assertTrue("The WRITE grant should give user2 write on the granted category",
        user2CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()));
    Assert.assertTrue("A WRITE grant should also satisfy the attach check",
        user2CategoryPermissions.userHasAttachAccessToCategory(parent.getResourceId()));
    // Unlike folder ACLs, the grant stops at the granted node: the permission Cypher walks
    // CONTAINS, but the category tree is linked with CONTAINSCATEGORY, so no inheritance occurs
    Assert.assertFalse("The WRITE grant on the parent category should not reach its child category",
        user2CategoryPermissions.userHasWriteAccessToCategory(child.getResourceId()));
    Assert.assertFalse("The grant should not make user2 the category owner",
        user2CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()));
  }

  @Test
  public void categoryAttachGrantDoesNotConferWrite() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Attach Only");

    grantUser2OnCategory(category.getResourceId(), CategoryPermission.ATTACH);

    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);
    Assert.assertTrue("The ATTACH grant should give user2 attach access",
        user2CategoryPermissions.userHasAttachAccessToCategory(category.getResourceId()));
    Assert.assertFalse("An ATTACH grant should not confer write access",
        user2CategoryPermissions.userHasWriteAccessToCategory(category.getResourceId()));
  }

  @Test
  public void categoryAttachesToAndDetachesFromArtifact() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Artifact Category");
    FolderServerArtifact template = createTemplateUnderUser1Home(newTemplate("Categorized Template", "0.0.1"));
    CedarTemplateId templateId = CedarTemplateId.build(template.getId());

    CategoryServiceSession user1Categories = categoriesOf(user1Context);
    Assert.assertTrue("Attaching the category to the template should succeed",
        user1Categories.attachCategoryToArtifact(category.getResourceId(), templateId));

    List<CedarCategoryId> attached = user1Categories.getAttachedCategoryIds(templateId);
    Assert.assertEquals("The template should carry exactly one attached category", 1, attached.size());
    Assert.assertEquals("The attached category should be the one just linked",
        category.getId(), attached.get(0).getId());

    Assert.assertTrue("Detaching the category should succeed",
        user1Categories.detachCategoryFromArtifact(category.getResourceId(), templateId));
    Assert.assertTrue("After the detach, the template should carry no categories",
        user1Categories.getAttachedCategoryIds(templateId).isEmpty());
  }

  @Test
  public void templateVersionChainIsNavigable() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerArtifact version1 = createTemplateUnderUser1Home(newTemplate("Version Chain Template", "0.0.1"));
    CedarTemplateId version1Id = CedarTemplateId.build(version1.getId());

    // A new version points at its predecessor; the create Cypher stores the previousVersion
    // property and adds the PREVIOUSVERSION relation in the same statement
    FolderServerTemplate secondVersion = newTemplate("Version Chain Template", "0.0.2");
    secondVersion.setPreviousVersion(CedarUntypedSchemaArtifactId.build(version1.getId()));
    FolderServerArtifact version2 = createTemplateUnderUser1Home(secondVersion);
    CedarTemplateId version2Id = CedarTemplateId.build(version2.getId());

    // The latest flags are caller-maintained, the way the resource server flips them after
    // creating a new version
    Assert.assertTrue("Clearing the latest flag on the old version should succeed",
        user1Folders.unsetLatestVersion(version1Id));
    Assert.assertTrue("Setting the latest flag on the new version should succeed",
        user1Folders.setLatestVersion(version2Id));

    FolderServerSchemaArtifact fresh1 = user1Folders.findSchemaArtifactById(version1Id);
    Assert.assertNotNull("The old version should stay retrievable", fresh1);
    Assert.assertEquals("The old version should not be flagged latest", Boolean.FALSE, fresh1.isLatestVersion());

    FolderServerSchemaArtifact fresh2 = user1Folders.findSchemaArtifactById(version2Id);
    Assert.assertNotNull("The new version should be retrievable", fresh2);
    Assert.assertEquals("The new version should be flagged latest", Boolean.TRUE, fresh2.isLatestVersion());
    Assert.assertNotNull("The new version should carry the previousVersion link", fresh2.getPreviousVersion());
    Assert.assertEquals("The previousVersion link should point at the old version",
        version1.getId(), fresh2.getPreviousVersion().getId());

    // The history query matches the longest PREVIOUSVERSION path through the queried node and
    // returns its nodes newest first, from either end of the chain
    List<FolderServerArtifactExtract> historyFromOld = user1Folders.getVersionHistory(version1Id);
    Assert.assertEquals("The history queried on the old version should span both versions", 2, historyFromOld.size());
    Assert.assertEquals("The history should list the new version first", version2.getId(), historyFromOld.get(0).getId());
    Assert.assertEquals("The history should list the old version last", version1.getId(), historyFromOld.get(1).getId());

    // The permission-filtered variant hides every node the caller cannot read; user2 has no
    // access anywhere under user1's home
    List<FolderServerArtifactExtract> historyForStranger =
        foldersOf(user2Context).getVersionHistoryWithPermission(version1Id);
    Assert.assertTrue("A stranger should see an empty permission-filtered version history",
        historyForStranger.isEmpty());
  }

}
