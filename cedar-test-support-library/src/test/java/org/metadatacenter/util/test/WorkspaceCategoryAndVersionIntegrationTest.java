package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtractWithChildren;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.CategoryNotEmptyException;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionTransitionConflictException;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.CategoryCapability;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroup;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionRequest;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUser;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Direct tests of the category graph and of artifact version chains, against an in-process
 * Neo4j. Category roles inherit down CONTAINSCATEGORY, while ownership remains separately
 * reported and supplies Manager authority to descendants.
 *
 * Version chains are graph state. A new version carries a previousVersion property and a
 * PREVIOUSVERSION relation to its predecessor. A plain create keeps the latest* flags the caller
 * set. Creating a draft, publishing and deleting recompute them for the whole series in the same
 * transaction.
 */
@Tag("integration")
public class WorkspaceCategoryAndVersionIntegrationTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarRequestContext adminContext;
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
    adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));

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
   * Grants user2 the given role on the category, as user1 (the owner).
   */
  private static void grantUser2OnCategory(CedarCategoryId categoryId, CategoryRole role) {
    CategoryPermissionRequest request = new CategoryPermissionRequest();
    request.setOwner(new CategoryPermissionUser(user1.getId()));
    request.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(user2.getId()), role));
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
  public void categoryDeletionRefusesChildrenAndAttachedArtifacts() {
    CategoryServiceSession categories = categoriesOf(user1Context);
    FolderServerCategory parent = createCategoryAsUser1(rootCategoryId, "Delete Guard Parent");
    FolderServerCategory child = createCategoryAsUser1(parent.getResourceId(), "Delete Guard Child");

    CategoryNotEmptyException childConflict = Assertions.assertThrows(CategoryNotEmptyException.class,
        () -> categories.deleteCategoryById(parent.getResourceId(), RevisionPrecondition.any()));
    Assertions.assertEquals(1L, childConflict.getChildCategoryCount());
    Assertions.assertEquals(0L, childConflict.getArtifactCount());
    Assertions.assertNotNull(categories.getCategoryById(parent.getResourceId()));
    Assertions.assertNotNull(categories.getCategoryById(child.getResourceId()));

    FolderServerCategory attached = createCategoryAsUser1(rootCategoryId, "Delete Guard Attached");
    FolderServerArtifact artifact = createTemplateUnderUser1Home(newTemplate("Delete Guard Template", "1.0.0"));
    Assertions.assertTrue(categories.attachCategoryToArtifact(
        attached.getResourceId(), CedarTemplateId.build(artifact.getId())));

    CategoryNotEmptyException artifactConflict = Assertions.assertThrows(CategoryNotEmptyException.class,
        () -> categories.deleteCategoryById(attached.getResourceId(), RevisionPrecondition.any()));
    Assertions.assertEquals(0L, artifactConflict.getChildCategoryCount());
    Assertions.assertEquals(1L, artifactConflict.getArtifactCount());
    Assertions.assertNotNull(categories.getCategoryById(attached.getResourceId()));
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
  public void rootCategoryKeepsItsStructuralRestrictionsAndEveryoneViewerGrant() {
    CategoryPermissionServiceSession adminPermissions = categoryPermissionsOf(adminContext);
    CategoryPermissionServiceSession user2Permissions = categoryPermissionsOf(user2Context);

    Assertions.assertTrue(user2Permissions.userHasCapability(rootCategoryId, CategoryCapability.READ_CATEGORY),
        "Everyone must receive Viewer on the root category");
    Assertions.assertFalse(user2Permissions.userHasCapability(rootCategoryId, CategoryCapability.ATTACH_CATEGORY));
    Assertions.assertTrue(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.CREATE_CHILD_CATEGORY));
    Assertions.assertTrue(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.MANAGE_GRANTS));
    Assertions.assertFalse(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.UPDATE_CATEGORY));
    Assertions.assertFalse(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.DELETE_CATEGORY));
    Assertions.assertFalse(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.MOVE_CATEGORY));
    Assertions.assertFalse(adminPermissions.userHasCapability(rootCategoryId, CategoryCapability.TRANSFER_OWNERSHIP));
  }

  @Test
  public void categoryManagerRoleInheritsAndIncludesClassification() {
    FolderServerCategory parent = createCategoryAsUser1(rootCategoryId, "Perm Parent");
    FolderServerCategory child = createCategoryAsUser1(parent.getResourceId(), "Perm Child");

    CategoryPermissionServiceSession user1CategoryPermissions = categoryPermissionsOf(user1Context);
    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);

    Assertions.assertTrue(user1CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()),
        "The creator should own the category");
    Assertions.assertTrue(user1CategoryPermissions.userHasCapability(
        parent.getResourceId(), CategoryCapability.MANAGE_GRANTS),
        "Ownership should confer Manager capabilities");
    Assertions.assertFalse(user2CategoryPermissions.userHasCapability(
        parent.getResourceId(), CategoryCapability.UPDATE_CATEGORY),
        "A Viewer should not edit the category");

    grantUser2OnCategory(parent.getResourceId(), CategoryRole.MANAGER);

    Assertions.assertTrue(user2CategoryPermissions.userHasCapability(
        parent.getResourceId(), CategoryCapability.MANAGE_GRANTS));
    Assertions.assertTrue(user2CategoryPermissions.userHasCapability(
        parent.getResourceId(), CategoryCapability.ATTACH_CATEGORY));
    Assertions.assertTrue(user2CategoryPermissions.userHasCapability(
        child.getResourceId(), CategoryCapability.MANAGE_GRANTS),
        "The Manager role should inherit down the category hierarchy");
    Assertions.assertFalse(user2CategoryPermissions.userIsOwnerOfCategory(parent.getResourceId()),
        "The grant should not make user2 the category owner");
  }

  @Test
  public void categoryClassifierRoleDoesNotConferEditing() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Attach Only");

    grantUser2OnCategory(category.getResourceId(), CategoryRole.CLASSIFIER);

    CategoryPermissionServiceSession user2CategoryPermissions = categoryPermissionsOf(user2Context);
    Assertions.assertTrue(user2CategoryPermissions.userHasCapability(
        category.getResourceId(), CategoryCapability.ATTACH_CATEGORY));
    Assertions.assertTrue(user2CategoryPermissions.userHasCapability(
        category.getResourceId(), CategoryCapability.DETACH_CATEGORY));
    Assertions.assertFalse(user2CategoryPermissions.userHasCapability(
        category.getResourceId(), CategoryCapability.UPDATE_CATEGORY));
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
        new CategoryPermissionUser(user2.getId()), CategoryRole.EDITOR));
    firstReplacement.getGroupPermissions().add(new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup(group.getId()), CategoryRole.MANAGER));
    BackendCallResult<VersionedCategoryPermissions> first = permissions.updateCategoryPermissions(
        category.getResourceId(), firstReplacement, RevisionPrecondition.exact(initial.revision()));
    Assertions.assertFalse(first.isError(), () -> first.getFirstErrorMessage());
    Assertions.assertEquals(2L, first.getPayload().revision());
    Assertions.assertEquals(CategoryRole.MANAGER,
        first.getPayload().content().getGroupPermissions().get(0).getRole(),
        "A Manager grant should round-trip through the legacy CANWRITECATEGORY relation");

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
  public void ownershipTransferIsAtomicAndRemovesTheNewOwnersDirectGrant() {
    FolderServerCategory category = createCategoryAsUser1(rootCategoryId, "Ownership Transfer Category");
    CategoryPermissionServiceSession permissions = categoryPermissionsOf(user1Context);

    CategoryPermissionRequest grant = new CategoryPermissionRequest();
    grant.setOwner(new CategoryPermissionUser(user1.getId()));
    grant.getUserPermissions().add(new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser(user2.getId()), CategoryRole.EDITOR));
    BackendCallResult<VersionedCategoryPermissions> granted = permissions.updateCategoryPermissions(
        category.getResourceId(), grant, RevisionPrecondition.exact(1));
    Assertions.assertFalse(granted.isError(), () -> granted.getFirstErrorMessage());

    Assertions.assertThrows(RevisionConflictException.class,
        () -> permissions.transferCategoryOwnership(category.getResourceId(), user2.getResourceId(),
            RevisionPrecondition.exact(1)));
    Assertions.assertEquals(user1.getId(),
        permissions.getCategoryPermissions(category.getResourceId()).getOwner().getId(),
        "A stale transfer must leave ownership unchanged");

    BackendCallResult<VersionedCategoryPermissions> transfer = permissions.transferCategoryOwnership(
        category.getResourceId(), user2.getResourceId(),
        RevisionPrecondition.exact(granted.getPayload().revision()));
    Assertions.assertFalse(transfer.isError(), () -> transfer.getFirstErrorMessage());
    Assertions.assertEquals(3L, transfer.getPayload().revision());
    Assertions.assertEquals(user2.getId(), transfer.getPayload().content().getOwner().getId());
    Assertions.assertTrue(transfer.getPayload().content().getUserPermissions().isEmpty(),
        "The new owner's redundant direct grant must be removed");
    Assertions.assertEquals(user2.getId(),
        categoriesOf(user1Context).getCategoryById(category.getResourceId()).getOwnedBy(),
        "The category's ownedBy property and OWNSCATEGORY relation must change together");
    Assertions.assertTrue(categoryPermissionsOf(user2Context).userIsOwnerOfCategory(category.getResourceId()));
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
  public void batchCategoryAttachmentAttachesAllAndIsAtomicWhenOneCategoryDoesNotExist() {
    FolderServerCategory first = createCategoryAsUser1(rootCategoryId, "Atomic Batch Category One");
    FolderServerCategory second = createCategoryAsUser1(rootCategoryId, "Atomic Batch Category Two");
    FolderServerArtifact successfulTemplate = createTemplateUnderUser1Home(
        newTemplate("Successful Atomic Batch Template", "0.0.1"));
    CedarTemplateId successfulTemplateId = CedarTemplateId.build(successfulTemplate.getId());
    CedarCategoryId missing = CedarCategoryId.build("https://repo.example/categories/missing-atomic-batch-category");

    CategoryServiceSession categories = categoriesOf(user1Context);
    Assertions.assertTrue(categories.attachCategoriesToArtifact(
        List.of(first.getResourceId(), second.getResourceId()), successfulTemplateId));
    Assertions.assertEquals(2, categories.getAttachedCategoryIds(successfulTemplateId).size());

    FolderServerArtifact failedTemplate = createTemplateUnderUser1Home(
        newTemplate("Failed Atomic Batch Template", "0.0.1"));
    CedarTemplateId failedTemplateId = CedarTemplateId.build(failedTemplate.getId());
    Assertions.assertFalse(categories.attachCategoriesToArtifact(
        List.of(first.getResourceId(), missing), failedTemplateId));
    Assertions.assertTrue(categories.getAttachedCategoryIds(failedTemplateId).isEmpty(),
        "a missing category must roll back the entire attachment batch");
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

    // The latest flags are not this chain's concern: the resource server recomputes them for the
    // whole series in one transaction, VersionChainTransaction.reconcile, and reindexes every member.
    FolderServerSchemaArtifact fresh2 = user1Folders.findSchemaArtifactById(version2Id);
    Assertions.assertNotNull(fresh2, "The new version should be retrievable");
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

  private static Map<NodeProperty, String> publication(String version) {
    return Map.of(NodeProperty.VERSION, version, NodeProperty.PUBLICATION_STATUS, BiboStatus.PUBLISHED.getValue());
  }

  private static FolderServerTemplate draftOf(FolderServerArtifact source, String version) {
    FolderServerTemplate draft = newTemplate(source.getName(), version);
    draft.setPreviousVersion(CedarUntypedSchemaArtifactId.build(source.getId()));
    return draft;
  }

  private static void assertFlags(CedarTemplateId id, boolean latest, boolean latestDraft, boolean latestPublished) {
    FolderServerSchemaArtifact artifact = foldersOf(user1Context).findSchemaArtifactById(id);
    Assertions.assertEquals(latest, artifact.isLatestVersion(), artifact.getVersion().getValue() + " latest");
    Assertions.assertEquals(latestDraft, artifact.isLatestDraftVersion(), artifact.getVersion().getValue() + " latest draft");
    Assertions.assertEquals(latestPublished, artifact.isLatestPublishedVersion(),
        artifact.getVersion().getValue() + " latest published");
  }

  private static ResourcePermissionServiceSession resourcePermissionsOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getResourcePermissionServiceSession(context);
  }

  /**
   * Publication recomputes the series' flags in the transaction that publishes, so the published
   * version is both the latest and the latest published one. A published version cannot be published
   * again.
   */
  @Test
  public void publishingADraftMakesItTheLatestPublishedVersionOnce() {
    FolderServiceSession folders = foldersOf(user1Context);
    CedarTemplateId id = CedarTemplateId.build(createTemplateUnderUser1Home(newTemplate("Publication Template", "1.0.0")).getId());

    Assertions.assertNotNull(folders.updateArtifactById(id, CedarResourceType.TEMPLATE, publication("1.0.0")));
    Assertions.assertEquals(BiboStatus.PUBLISHED, folders.findSchemaArtifactById(id).getPublicationStatus());
    assertFlags(id, true, false, true);

    Assertions.assertThrows(VersionTransitionConflictException.class,
        () -> folders.updateArtifactById(id, CedarResourceType.TEMPLATE, publication("1.0.1")));
    Assertions.assertEquals("1.0.0", folders.findSchemaArtifactById(id).getVersion().getValue(),
        "a refused publication must leave the version as it was");
  }

  /**
   * A draft of the latest published version becomes the series' only draft and its latest version,
   * and the source keeps only its latest-published flag. When asked to, the draft starts with the
   * source's grants. The source accepts one successor, a draft cannot itself be drafted from, and
   * deleting the draft hands the latest flag back to the source.
   */
  @Test
  public void aDraftOfThePublishedVersionIsItsSoleSuccessorAndMayCarryItsSharing() {
    FolderServiceSession folders = foldersOf(user1Context);
    FolderServerArtifact source = createTemplateUnderUser1Home(newTemplate("Draft Source Template", "1.0.0"));
    CedarTemplateId sourceId = CedarTemplateId.build(source.getId());
    Assertions.assertNotNull(folders.updateArtifactById(sourceId, CedarResourceType.TEMPLATE, publication("1.0.0")));
    ResourcePermissionsRequest share = new ResourcePermissionsRequest();
    share.setOwner(new ResourcePermissionUser(user1.getId()));
    share.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.VIEWER));
    Assertions.assertFalse(resourcePermissionsOf(user1Context).updateResourcePermissions(sourceId, share).isError());

    FolderServerArtifact draft = folders.createDraftWithCleanup(draftOf(source, "2.0.0"), user1HomeId, true, null);
    Assertions.assertNotNull(draft);
    CedarTemplateId draftId = CedarTemplateId.build(draft.getId());
    assertFlags(draftId, true, true, false);
    assertFlags(sourceId, false, false, true);
    Assertions.assertTrue(resourcePermissionsOf(user2Context).userHasRole(draftId, ResourceRole.VIEWER),
        "the draft should carry the source's grant");
    Assertions.assertEquals(List.of(draft.getId(), source.getId()),
        folders.getVersionHistory(draftId).stream().map(FolderServerArtifactExtract::getId).toList());

    FolderServerTemplate rival = draftOf(source, "2.0.1");
    Assertions.assertThrows(VersionTransitionConflictException.class,
        () -> folders.createDraftWithCleanup(rival, user1HomeId, false, null));
    Assertions.assertNull(folders.findSchemaArtifactById(CedarTemplateId.build(rival.getId())),
        "a refused draft must leave no node behind");
    FolderServerTemplate draftOfDraft = draftOf(draft, "3.0.0");
    Assertions.assertThrows(VersionTransitionConflictException.class,
        () -> folders.createDraftWithCleanup(draftOfDraft, user1HomeId, false, null));

    Assertions.assertTrue(folders.deleteResourceById(draftId));
    assertFlags(sourceId, true, false, true);

    FolderServerArtifact unshared = folders.createDraftWithCleanup(draftOf(source, "2.0.0"), user1HomeId, false, null);
    Assertions.assertNotNull(unshared, "once the draft is gone the source accepts a successor again");
    Assertions.assertFalse(resourcePermissionsOf(user2Context).userHasRole(
        CedarTemplateId.build(unshared.getId()), ResourceRole.VIEWER), "a draft made without sharing starts unshared");
  }

  private static FolderServerCategoryExtractWithChildren childOf(FolderServerCategoryExtractWithChildren parent,
                                                                  FolderServerCategory child) {
    return parent.getChildren().stream()
        .filter(extract -> extract.getId().equals(child.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(child.getName() + " should sit under " + parent.getId()));
  }

  /** The tree hangs every category under its own parent, at any depth. */
  @Test
  public void categoryTreeNestsEachCategoryUnderItsParent() {
    FolderServerCategory a = createCategoryAsUser1(rootCategoryId, "Nesting A");
    FolderServerCategory b = createCategoryAsUser1(a.getResourceId(), "Nesting B");
    FolderServerCategory c = createCategoryAsUser1(b.getResourceId(), "Nesting C");
    FolderServerCategory d = createCategoryAsUser1(rootCategoryId, "Nesting D");

    FolderServerCategoryExtractWithChildren root = categoriesOf(user1Context).getCategoryTree();

    Assertions.assertEquals(rootCategoryId.getId(), root.getId());
    Assertions.assertTrue(childOf(root, d).getChildren().isEmpty());
    FolderServerCategoryExtractWithChildren treeA = childOf(root, a);
    Assertions.assertEquals(List.of(b.getId()), treeA.getChildren().stream().map(FolderServerCategoryExtract::getId).toList());
    FolderServerCategoryExtractWithChildren treeB = childOf(treeA, b);
    Assertions.assertEquals(List.of(c.getId()), treeB.getChildren().stream().map(FolderServerCategoryExtract::getId).toList());
    Assertions.assertTrue(childOf(treeB, c).getChildren().isEmpty());
  }

  /**
   * An artifact's category paths run from the root to each category attached to it, one path per
   * attachment. Its materialized categories, which the search index filters by, are every category on
   * those paths, so a search by an ancestor finds the artifact.
   */
  @Test
  public void attachedCategoryPathsRunFromTheRootToEachAttachedCategory() {
    FolderServerCategory a = createCategoryAsUser1(rootCategoryId, "Paths A");
    FolderServerCategory b = createCategoryAsUser1(a.getResourceId(), "Paths B");
    FolderServerCategory c = createCategoryAsUser1(rootCategoryId, "Paths C");
    CedarTemplateId templateId = CedarTemplateId.build(
        createTemplateUnderUser1Home(newTemplate("Category Paths Template", "0.0.1")).getId());
    CategoryServiceSession categories = categoriesOf(user1Context);
    Assertions.assertTrue(categories.attachCategoriesToArtifact(List.of(b.getResourceId(), c.getResourceId()), templateId));

    Set<List<String>> paths = categories.getAttachedCategoryPaths(templateId).stream()
        .map(path -> path.stream().map(FolderServerCategoryExtract::getId).toList())
        .collect(Collectors.toSet());

    Assertions.assertEquals(Set.of(List.of(rootCategoryId.getId(), a.getId(), b.getId()),
        List.of(rootCategoryId.getId(), c.getId())), paths);
    Assertions.assertEquals(Set.of(rootCategoryId.getId(), a.getId(), b.getId(), c.getId()),
        categories.getArtifactMaterializedCategories(templateId).getCategoryIds());

    CedarTemplateId missing = CedarTemplateId.build(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    Assertions.assertEquals(List.of(), categories.getAttachedCategoryPaths(missing));
    Assertions.assertNull(categories.getArtifactMaterializedCategories(missing));
  }

}
