package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroup;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroupPermissionPair;
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

    // Seeding creates the root category the way provisioning does
    FolderServerCategory rootCategory = categoriesOf(user1Context).getRootCategory();
    Assertions.assertNotNull(rootCategory, "The seeded graph should contain the root category");
    rootCategoryId = rootCategory.getResourceId();
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getFolderServiceSession(context);
  }

  private static CategoryServiceSession categoriesOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getCategoryServiceSession(context);
  }

  private static CategoryPermissionServiceSession categoryPermissionsOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getCategoryPermissionServiceSession(context);
  }

  private static FolderServerCategory createCategoryAsUser1(CedarCategoryId parentId, String name) {
    FolderServerCategory created = categoriesOf(user1Context).createCategory(parentId, name,
        "Created by WorkspaceCategoryAndVersionIntegrationTest", null);
    Assertions.assertNotNull(created, "The category '" + name + "' should be created");
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
      Assertions.fail("The category permission update should succeed: " + result.getFirstErrorMessage());
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
    Assertions.assertNotNull(created, "The template '" + template.getName() + "' should be created");
    return created;
  }

  @Test
  public void staleCategoryDeletePreservesANewerEdit() {
    CategoryServiceSession categories = categoriesOf(user1Context);
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Versioned Delete Category");
    VersionedResource<FolderServerCategory> initial = categories.getVersionedCategoryById(category.getResourceId());
    Assertions.assertEquals(1L, initial.revision());

    categories.updateCategoryById(category.getResourceId(),
        Map.of(org.metadatacenter.server.neo4j.cypher.NodeProperty.DESCRIPTION, "newer description"));
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> categories.deleteCategoryById(category.getResourceId(), RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());
    Assertions.assertEquals("newer description",
        categories.getCategoryById(category.getResourceId()).getDescription());
    Assertions.assertTrue(categories.deleteCategoryById(category.getResourceId(), RevisionPrecondition.any()));
  }

  @Test
  public void categoryTreeIsCreatedAndTraversable() {
    FolderServerCategory child = createCategoryAsUser1(rootCategoryId, "Tree Child");
    FolderServerCategory grandchild = createCategoryAsUser1(child.getResourceId(), "Tree Grandchild");

    CategoryServiceSession user1Categories = categoriesOf(user1Context);
    FolderServerCategory foundByParentAndName = user1Categories.getCategoryByParentAndName(rootCategoryId, "Tree Child");
    Assertions.assertNotNull(foundByParentAndName, "The child category should be found under the root by name");
    Assertions.assertEquals(child.getId(), foundByParentAndName.getId(),
        "The lookup under the root should return the created node");

    Assertions.assertEquals(
        child.getId(),
        user1Categories.getCategoryById(grandchild.getResourceId()).getParentCategoryId(),
        "The grandchild should record its parent category");

    List<FolderServerCategoryExtract> path = user1Categories.getCategoryPath(grandchild.getResourceId());
    Assertions.assertEquals(3, path.size(), "The grandchild's category path should span root, child and grandchild");
    List<String> pathIds = path.stream().map(FolderServerCategoryExtract::getId).toList();
    Assertions.assertTrue(pathIds.contains(rootCategoryId.getId()), "The path should contain the root category");
    Assertions.assertTrue(pathIds.contains(child.getId()), "The path should contain the child category");
    Assertions.assertTrue(pathIds.contains(grandchild.getId()), "The path should contain the grandchild category");
  }

  @Test
  public void categoryWriteGrantIsPerNodeAndImpliesAttach() {
    FolderServerCategory parent = createCategoryAsUser1(rootCategoryId, "Perm Parent");
    FolderServerCategory child = createCategoryAsUser1(parent.getResourceId(), "Perm Child");

    CategoryPermissionServiceSession user1CategoryPermissions = categoryPermissionsOf(user1Context);
    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);

    Assertions.assertTrue(user1CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()),
        "The creator should own the category");
    Assertions.assertTrue(user1CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()),
        "Ownership should confer write on the owned node");
    Assertions.assertFalse(user2CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()),
        "A stranger should not write the category before any grant");
    Assertions.assertFalse(user2CategoryPermissions.userHasAttachAccessToCategory(parent.getResourceId()),
        "A stranger should not attach to the category before any grant");

    grantUser2OnCategory(parent.getResourceId(), CategoryPermission.WRITE);

    Assertions.assertTrue(user2CategoryPermissions.userHasWriteAccessToCategory(parent.getResourceId()),
        "The WRITE grant should give user2 write on the granted category");
    Assertions.assertTrue(user2CategoryPermissions.userHasAttachAccessToCategory(parent.getResourceId()),
        "A WRITE grant should also satisfy the attach check");
    // Unlike folder ACLs, the grant stops at the granted node: the permission Cypher walks
    // CONTAINS, but the category tree is linked with CONTAINSCATEGORY, so no inheritance occurs
    Assertions.assertFalse(user2CategoryPermissions.userHasWriteAccessToCategory(child.getResourceId()),
        "The WRITE grant on the parent category should not reach its child category");
    Assertions.assertFalse(user2CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()),
        "The grant should not make user2 the category owner");
  }

  @Test
  public void categoryAttachGrantDoesNotConferWrite() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Attach Only");

    grantUser2OnCategory(category.getResourceId(), CategoryPermission.ATTACH);

    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);
    Assertions.assertTrue(user2CategoryPermissions.userHasAttachAccessToCategory(category.getResourceId()),
        "The ATTACH grant should give user2 attach access");
    Assertions.assertFalse(user2CategoryPermissions.userHasWriteAccessToCategory(category.getResourceId()),
        "An ATTACH grant should not confer write access");
  }

  @Test
  public void staleCategoryPermissionReplacementIsRejectedWithoutChangingTheAcl() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Versioned Category ACL");
    GroupServiceSession groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = groups.createGroup("versioned-category-acl-group",
        "Group for the category ACL revision test");
    Assertions.assertNotNull(group);

    CategoryPermissionServiceSession permissions = categoryPermissionsOf(user1Context);
    VersionedCategoryPermissions initial = permissions.getVersionedCategoryPermissions(category.getResourceId());
    Assertions.assertEquals(1L, initial.revision());

    CategoryPermissionRequest firstReplacement = new CategoryPermissionRequest();
    firstReplacement.setOwner(new CategoryPermissionUser(user1.getId()));
    firstReplacement.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(user2.getId()), CategoryPermission.WRITE));
    firstReplacement.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(group.getId()), CategoryPermission.WRITE));
    BackendCallResult<VersionedCategoryPermissions> first = permissions.updateCategoryPermissions(
        category.getResourceId(), firstReplacement, RevisionPrecondition.exact(initial.revision()));
    Assertions.assertFalse(first.isError(), () -> first.getFirstErrorMessage());
    Assertions.assertEquals(2L, first.getPayload().revision());
    Assertions.assertEquals(CategoryPermission.WRITE,
        first.getPayload().content().getGroupPermissions().get(0).getPermission(),
        "A group WRITE grant should round-trip as the category WRITE relation");

    CategoryPermissionRequest staleReplacement = new CategoryPermissionRequest();
    staleReplacement.setOwner(new CategoryPermissionUser(user1.getId()));
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> permissions.updateCategoryPermissions(category.getResourceId(), staleReplacement,
            RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());

    VersionedCategoryPermissions fresh = permissions.getVersionedCategoryPermissions(category.getResourceId());
    Assertions.assertEquals(2L, fresh.revision());
    Assertions.assertEquals(1, fresh.content().getUserPermissions().size());
    Assertions.assertEquals(user2.getId(), fresh.content().getUserPermissions().get(0).getUser().getId());
    Assertions.assertEquals(1, fresh.content().getGroupPermissions().size());
    Assertions.assertEquals(group.getId(), fresh.content().getGroupPermissions().get(0).getGroup().getId());
  }

  @Test
  public void categoryAttachesToAndDetachesFromArtifact() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Artifact Category");
    FolderServerArtifact template = createTemplateUnderUser1Home(newTemplate("Categorized Template", "0.0.1"));
    CedarTemplateId templateId = CedarTemplateId.build(template.getId());

    CategoryServiceSession user1Categories = categoriesOf(user1Context);
    Assertions.assertTrue(user1Categories.attachCategoryToArtifact(category.getResourceId(), templateId),
        "Attaching the category to the template should succeed");

    List<CedarCategoryId> attached = user1Categories.getAttachedCategoryIds(templateId);
    Assertions.assertEquals(1, attached.size(), "The template should carry exactly one attached category");
    Assertions.assertEquals(category.getId(), attached.get(0).getId(),
        "The attached category should be the one just linked");

    Assertions.assertTrue(user1Categories.detachCategoryFromArtifact(category.getResourceId(), templateId),
        "Detaching the category should succeed");
    Assertions.assertTrue(user1Categories.getAttachedCategoryIds(templateId).isEmpty(),
        "After the detach, the template should carry no categories");
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
    Assertions.assertTrue(user1Folders.unsetLatestVersion(version1Id),
        "Clearing the latest flag on the old version should succeed");
    Assertions.assertTrue(user1Folders.setLatestVersion(version2Id),
        "Setting the latest flag on the new version should succeed");

    FolderServerSchemaArtifact fresh1 = user1Folders.findSchemaArtifactById(version1Id);
    Assertions.assertNotNull(fresh1, "The old version should stay retrievable");
    Assertions.assertEquals(Boolean.FALSE, fresh1.isLatestVersion(), "The old version should not be flagged latest");

    FolderServerSchemaArtifact fresh2 = user1Folders.findSchemaArtifactById(version2Id);
    Assertions.assertNotNull(fresh2, "The new version should be retrievable");
    Assertions.assertEquals(Boolean.TRUE, fresh2.isLatestVersion(), "The new version should be flagged latest");
    Assertions.assertNotNull(fresh2.getPreviousVersion(), "The new version should carry the previousVersion link");
    Assertions.assertEquals(version1.getId(), fresh2.getPreviousVersion().getId(),
        "The previousVersion link should point at the old version");

    // The history query matches the longest PREVIOUSVERSION path through the queried node and
    // returns its nodes newest first, from either end of the chain
    List<FolderServerArtifactExtract> historyFromOld = user1Folders.getVersionHistory(version1Id);
    Assertions.assertEquals(2, historyFromOld.size(),
        "The history queried on the old version should span both versions");
    Assertions.assertEquals(version2.getId(), historyFromOld.get(0).getId(),
        "The history should list the new version first");
    Assertions.assertEquals(version1.getId(), historyFromOld.get(1).getId(),
        "The history should list the old version last");

    // The permission-filtered variant hides every node the caller cannot read; user2 has no
    // access anywhere under user1's home
    List<FolderServerArtifactExtract> historyForStranger =
        foldersOf(user2Context).getVersionHistoryWithPermission(version1Id);
    Assertions.assertTrue(historyForStranger.isEmpty(),
        "A stranger should see an empty permission-filtered version history");
  }

}
