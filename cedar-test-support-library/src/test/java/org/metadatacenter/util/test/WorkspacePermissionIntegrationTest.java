package org.metadatacenter.util.test;

import org.metadatacenter.model.CedarResourceType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GroupCallerMembership;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.VersionedGroupUsers;
import org.metadatacenter.server.VersionedResourcePermissions;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUserRequest;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroup;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Direct tests of the workspace graph layer against an in-process Neo4j: the ACL evaluation in
 * ResourcePermissionServiceSession (owner, direct user grant, group-resolved grant) and the
 * Cypher-built folder operations in FolderServiceSession. No server is booted; the sessions are
 * constructed the way the applications construct them, from a request context carrying the test
 * user. Redis is redirected to a dead port: the query-logging queue writes are best-effort, and
 * this enforces that no session operation under test depends on a live Redis.
 */
@Tag("integration")
public class WorkspacePermissionIntegrationTest {

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

    // Returns the singleton instance startRedirectAndSeed already built
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

  /**
   * Creates a folder under user1's home through the real Cypher create path, owned by user1.
   */
  private static FolderServerFolder createFolderUnderUser1Home(String name) {
    return createFolderUnder(user1HomeId, name);
  }

  private static FolderServerFolder createFolderUnder(CedarFolderId parentId, String name) {
    FolderServerFolder newFolder = new FolderServerFolder();
    newFolder.setName(name);
    newFolder.setDescription("Created by WorkspacePermissionIntegrationTest");
    CedarFolderId newFolderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(newFolder, parentId, newFolderId);
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
    return created;
  }

  /** A permission update request that explicitly restates the unchanged owner. */
  private static ResourcePermissionsRequest requestOwnedByUser1() {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    return request;
  }

  private static void updatePermissionsAsUser1(FolderServerFolder folder, ResourcePermissionsRequest request) {
    BackendCallResult result = permissionsOf(user1Context).updateResourcePermissions(folder.getResourceId(), request);
    if (result.isError()) {
      Assertions.fail("The permission update should succeed: " + result.getFirstErrorMessage());
    }
  }

  @Test
  public void ownerHasReadAndWriteAccessToHomeFolder() {
    ResourcePermissionServiceSession user1Permissions = permissionsOf(user1Context);
    Assertions.assertTrue(user1Permissions.userHasRole(user1HomeId, ResourceRole.VIEWER),
        "The owner should have read access to the home folder");
    Assertions.assertTrue(user1Permissions.userHasRole(user1HomeId, ResourceRole.MANAGER),
        "The owner should have write access to the home folder");
    Assertions.assertTrue(user1Permissions.userIsOwnerOfResource(user1HomeId),
        "The home folder should report user1 as its owner");
  }

  @Test
  public void strangerHasNoAccessToHomeFolder() {
    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasRole(user1HomeId, ResourceRole.VIEWER),
        "A stranger should have no read access to another user's home folder");
    Assertions.assertFalse(user2Permissions.userHasRole(user1HomeId, ResourceRole.MANAGER),
        "A stranger should have no write access to another user's home folder");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(user1HomeId),
        "A stranger should not be reported as the owner of another user's home folder");
  }

  @Test
  public void directUserGrantGivesReadButNotWrite() {
    FolderServerFolder folder = createFolderUnderUser1Home("Direct Grant Folder");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.VIEWER),
        "Before the grant, user2 should have no read access");

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.VIEWER));
    updatePermissionsAsUser1(folder, request);

    Assertions.assertTrue(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.VIEWER),
        "After a READ grant, user2 should have read access");
    Assertions.assertFalse(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.EDITOR),
        "A READ grant should not confer write access");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(folder.getResourceId()),
        "A READ grant should not confer ownership");
  }

  @Test
  public void eachDirectRoleProvidesExactlyItsDefinedCapabilities() {
    for (ResourceRole role : ResourceRole.values()) {
      FolderServerFolder folder = createFolderUnderUser1Home("Direct Role " + role.getValue());
      ResourcePermissionsRequest request = requestOwnedByUser1();
      request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
          new ResourcePermissionUser(user2.getId()), role));
      updatePermissionsAsUser1(folder, request);

      ResourcePermissionServiceSession permissions = permissionsOf(user2Context);
      Assertions.assertEquals(role, permissions.getResourceAuthority(folder.getResourceId()).highestSatisfiedRole());
      for (ResourceCapability capability : ResourceCapability.values()) {
        boolean expected = capability != ResourceCapability.TRANSFER_OWNERSHIP
            && role.provides(CedarResourceType.FOLDER, capability);
        Assertions.assertEquals(expected,
            permissions.userHasCapability(folder.getResourceId(), capability),
            role + " has the wrong result for " + capability);
      }
    }
  }

  @Test
  public void ownershipProvidesManagerCapabilitiesAndTransfer() {
    FolderServerFolder ownedFolder = createFolderUnderUser1Home("Owned Capability Folder");
    ResourcePermissionServiceSession permissions = permissionsOf(user1Context);
    var authority = permissions.getResourceAuthority(ownedFolder.getResourceId());
    Assertions.assertTrue(authority.owner());
    Assertions.assertNull(authority.role(), "ownership must not be reported as a resource role");
    Assertions.assertEquals(ResourceRole.MANAGER, authority.highestSatisfiedRole());
    for (ResourceCapability capability : ResourceCapability.values()) {
      Assertions.assertTrue(permissions.userHasCapability(ownedFolder.getResourceId(), capability),
          "ownership should provide " + capability);
    }
  }

  @Test
  public void everyoneMayReceiveViewerButNotEditorOrManager() {
    FolderServerGroup everyone = CedarDataServices.getInstance().getGroupServiceSession(user1Context)
        .findGroupByName(cedarConfig.getFolderStructureConfig().getEverybodyGroup().getName());
    Assertions.assertNotNull(everyone);

    for (ResourceRole role : ResourceRole.values()) {
      FolderServerFolder folder = createFolderUnderUser1Home("Everyone " + role.getValue());
      ResourcePermissionsRequest request = requestOwnedByUser1();
      request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
          new ResourcePermissionGroup(everyone.getId()), role));
      BackendCallResult result = permissionsOf(user1Context)
          .updateResourcePermissions(folder.getResourceId(), request);

      if (role == ResourceRole.VIEWER) {
        Assertions.assertFalse(result.isError(), "Everyone + Viewer should be accepted");
        Assertions.assertEquals(ResourceRole.VIEWER,
            permissionsOf(user2Context).getResourceAuthority(folder.getResourceId()).highestSatisfiedRole());
      } else {
        Assertions.assertTrue(result.isError(), "Everyone + " + role + " should be rejected");
        Assertions.assertNull(permissionsOf(user2Context)
            .getResourceAuthority(folder.getResourceId()).highestSatisfiedRole());
      }
    }
  }

  @Test
  public void groupWriteGrantResolvesThroughMembership() {
    FolderServerFolder folder = createFolderUnderUser1Home("Group Grant Folder");

    GroupServiceSession user1Groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = user1Groups.createGroup("workspace-integration-test-group",
        "Group for the group-resolved ACL test");
    Assertions.assertNotNull(group, "The group should be created");
    Assertions.assertTrue(user1Groups.userAdministersGroup(group.getResourceId()),
        "The creator should administer the new group");

    // Membership updates replace the full member and administrator sets, so the request lists
    // user1 (creator, administrator) alongside the new member user2
    CedarGroupUsersRequest membership = new CedarGroupUsersRequest();
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    BackendCallResult membershipResult = user1Groups.updateGroupUsers(group.getResourceId(), membership);
    Assertions.assertFalse(membershipResult.isError(), "The membership update should succeed");

    ResourcePermissionServiceSession user2Permissions = permissionsOf(user2Context);
    Assertions.assertFalse(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.VIEWER),
        "Group membership alone should confer nothing before the grant");

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(group.getId()), ResourceRole.MANAGER));
    updatePermissionsAsUser1(folder, request);

    Assertions.assertTrue(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.MANAGER),
        "A WRITE grant to the group should give the member write access");
    Assertions.assertTrue(user2Permissions.userHasRole(folder.getResourceId(), ResourceRole.VIEWER),
        "Write access through the group should imply read access");
    Assertions.assertFalse(user2Permissions.userIsOwnerOfResource(folder.getResourceId()),
        "The group grant should not make the member the owner");
  }

  @Test
  public void folderLifecycleRunsThroughTheCypherLayer() {
    FolderServiceSession user1Folders = foldersOf(user1Context);

    FolderServerFolder parent = createFolderUnderUser1Home("Lifecycle Parent");
    FolderServerFolder child = createFolderUnder(parent.getResourceId(), "Lifecycle Child");

    // Find by id
    FolderServerFolder foundById = user1Folders.findFolderById(child.getResourceId());
    Assertions.assertNotNull(foundById, "The nested folder should be found by id");
    Assertions.assertEquals("Lifecycle Child", foundById.getName(),
        "The folder found by id should carry the created name");

    // Find through the parent listing
    FileSystemResource foundInParent =
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child");
    Assertions.assertNotNull(foundInParent, "The nested folder should be found under its parent by name");
    Assertions.assertEquals(child.getId(), foundInParent.getId(),
        "The lookup under the parent should return the same node");

    // Rename; the graph stores the lowercased name alongside, for case-insensitive ordering
    Map<NodeProperty, String> updateFields = new HashMap<>();
    updateFields.put(NodeProperty.NAME, "Lifecycle Child Renamed");
    updateFields.put(NodeProperty.NAME_LOWER, "lifecycle child renamed");
    FolderServerFolder renamed = user1Folders.updateFolderById(child.getResourceId(), updateFields);
    Assertions.assertNotNull(renamed, "The rename should return the updated folder");
    Assertions.assertEquals("Lifecycle Child Renamed", renamed.getName(), "The rename should persist the new name");
    Assertions.assertEquals("Lifecycle Child Renamed", user1Folders.findFolderById(child.getResourceId()).getName(),
        "The rename should be visible on a fresh read");

    // Delete, leaf first
    Assertions.assertTrue(user1Folders.deleteFolderById(child.getResourceId()),
        "Deleting the nested folder should succeed");
    Assertions.assertNull(user1Folders.findFolderById(child.getResourceId()),
        "The deleted folder should no longer be found by id");
    Assertions.assertNull(
        user1Folders.findFilesystemResourceByParentFolderIdAndName(parent.getResourceId(), "Lifecycle Child Renamed"),
        "The deleted folder should no longer appear under its parent");

    Assertions.assertTrue(user1Folders.deleteFolderById(parent.getResourceId()),
        "Deleting the emptied parent should succeed");
    Assertions.assertNull(user1Folders.findFolderById(parent.getResourceId()),
        "The deleted parent should no longer be found by id");
  }

  @Test
  public void deletingANonEmptyFolderPreservesItsWholeSubtree() {
    FolderServiceSession user1Folders = foldersOf(user1Context);
    FolderServerFolder parent = createFolderUnderUser1Home("Protected Nonempty Parent");
    FolderServerFolder child = createFolderUnder(parent.getResourceId(), "Protected Child");

    Assertions.assertFalse(user1Folders.deleteFolderById(parent.getResourceId()),
        "Deleting a non-empty folder should be refused atomically");
    Assertions.assertNotNull(user1Folders.findFolderById(parent.getResourceId()),
        "The refused deletion must preserve the parent");
    Assertions.assertNotNull(user1Folders.findFolderById(child.getResourceId()),
        "The refused deletion must preserve the child");
    Assertions.assertNotNull(user1Folders.findFilesystemResourceByParentFolderIdAndName(
            parent.getResourceId(), child.getName()),
        "The refused deletion must preserve the parent-child relationship");
  }

  @Test
  public void staleFolderDeletePreservesANewerRename() {
    FolderServiceSession folders = foldersOf(user1Context);
    FolderServerFolder folder = createFolderUnderUser1Home("Versioned Delete Folder");
    VersionedResource<FolderServerFolder> initial = folders.findVersionedFolderById(folder.getResourceId());
    Assertions.assertEquals(1L, initial.revision());

    Map<NodeProperty, String> update = new HashMap<>();
    update.put(NodeProperty.NAME, "Versioned Delete Folder Renamed");
    update.put(NodeProperty.NAME_LOWER, "versioned delete folder renamed");
    folders.updateFolderById(folder.getResourceId(), update);

    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> folders.deleteFolderById(folder.getResourceId(), RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());
    Assertions.assertEquals("Versioned Delete Folder Renamed",
        folders.findFolderById(folder.getResourceId()).getName());
    Assertions.assertTrue(folders.deleteFolderById(folder.getResourceId(), RevisionPrecondition.any()));
  }

  @Test
  public void staleGroupDeletePreservesANewerEdit() {
    GroupServiceSession groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = groups.createGroup("versioned-delete-group", "before edit");
    VersionedResource<FolderServerGroup> initial = groups.findVersionedGroupById(group.getResourceId());
    Assertions.assertEquals(1L, initial.revision());

    groups.updateGroupById(group.getResourceId(), Map.of(NodeProperty.DESCRIPTION, "after edit"));
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> groups.deleteGroupById(group.getResourceId(), RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());
    Assertions.assertEquals("after edit", groups.findGroupById(group.getResourceId()).getDescription());
    Assertions.assertTrue(groups.deleteGroupById(group.getResourceId(), RevisionPrecondition.any()));
  }

  @Test
  public void staleGroupMembershipReplacementIsRejectedWithoutChangingTheAggregate() {
    GroupServiceSession groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    FolderServerGroup group = groups.createGroup("versioned-membership-integration-group",
        "Group for the membership revision test");
    Assertions.assertNotNull(group);

    VersionedGroupUsers initial = groups.findVersionedGroupUsers(group.getResourceId());
    Assertions.assertEquals(1L, initial.revision());
    Assertions.assertEquals(1, initial.content().getUsers().size());

    CedarGroupUsersRequest twoUsers = new CedarGroupUsersRequest();
    twoUsers.getUsers().add(new CedarGroupUserRequest(
        new ResourcePermissionUser(user1.getId()), true, true));
    twoUsers.getUsers().add(new CedarGroupUserRequest(
        new ResourcePermissionUser(user2.getId()), false, true));
    BackendCallResult<VersionedGroupUsers> first = groups.updateGroupUsers(
        group.getResourceId(), twoUsers, RevisionPrecondition.exact(initial.revision()));
    Assertions.assertFalse(first.isError(), () -> first.getFirstErrorMessage());
    Assertions.assertEquals(2L, first.getPayload().revision());
    Assertions.assertEquals(2, first.getPayload().content().getUsers().size());

    CedarGroupUsersRequest staleReplacement = new CedarGroupUsersRequest();
    staleReplacement.getUsers().add(new CedarGroupUserRequest(
        new ResourcePermissionUser(user1.getId()), true, true));
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> groups.updateGroupUsers(group.getResourceId(), staleReplacement,
            RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());

    VersionedGroupUsers after = groups.findVersionedGroupUsers(group.getResourceId());
    Assertions.assertEquals(2L, after.revision());
    Assertions.assertEquals(2, after.content().getUsers().size(),
        "The stale replacement must not remove the second member");
  }

  @Test
  public void staleResourcePermissionReplacementIsRejectedWithoutChangingTheAggregate() {
    FolderServerFolder folder = createFolderUnderUser1Home("Versioned ACL Folder");
    ResourcePermissionServiceSession permissions = permissionsOf(user1Context);

    VersionedResourcePermissions initial = permissions.getVersionedResourcePermissions(folder.getResourceId());
    Assertions.assertEquals(1L, initial.revision());
    Assertions.assertEquals(user1.getId(), initial.content().getOwner().getId());

    ResourcePermissionsRequest shared = requestOwnedByUser1();
    shared.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.VIEWER));
    BackendCallResult<VersionedResourcePermissions> first = permissions.updateResourcePermissions(
        folder.getResourceId(), shared, RevisionPrecondition.exact(initial.revision()));
    Assertions.assertFalse(first.isError(), () -> first.getFirstErrorMessage());
    Assertions.assertEquals(2L, first.getPayload().revision());
    Assertions.assertEquals(1, first.getPayload().content().getUserPermissions().size());

    ResourcePermissionsRequest staleReplacement = requestOwnedByUser1();
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> permissions.updateResourcePermissions(folder.getResourceId(), staleReplacement,
            RevisionPrecondition.exact(initial.revision())));
    Assertions.assertEquals(2L, conflict.getCurrentRevision());

    VersionedResourcePermissions after = permissions.getVersionedResourcePermissions(folder.getResourceId());
    Assertions.assertEquals(2L, after.revision());
    Assertions.assertEquals(1, after.content().getUserPermissions().size(),
        "The stale replacement must not remove the direct user grant");
    Assertions.assertEquals(user2.getId(), after.content().getUserPermissions().get(0).getUser().getId());
  }

  /**
   * Each precondition of an ownership transfer refuses on its own and leaves the owner in place. The
   * resource must exist, the caller must own it, and the new owner must be another user the graph
   * holds. A transfer under a stale revision is a conflict.
   */
  @Test
  public void resourceOwnershipTransferRefusesEachPreconditionAndKeepsTheOwner() {
    FolderServerFolder folder = createFolderUnderUser1Home("Transfer Refusal Folder");
    CedarFolderId missingFolder = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    CedarUserId unknownUser = CedarUserId.build("https://metadatacenter.org/users/" + UUID.randomUUID());
    ResourcePermissionServiceSession user1Permissions = permissionsOf(user1Context);
    long revision = user1Permissions.getVersionedResourcePermissions(folder.getResourceId()).revision();

    assertRefused(user1Permissions.transferResourceOwnership(missingFolder, user2.getResourceId(),
        RevisionPrecondition.any()), CedarErrorType.NOT_FOUND, CedarErrorKey.NODE_NOT_FOUND);
    assertRefused(permissionsOf(user2Context).transferResourceOwnership(folder.getResourceId(),
        user2.getResourceId(), RevisionPrecondition.any()), CedarErrorType.PERMISSION, CedarErrorKey.NOT_AUTHORIZED);
    assertRefused(user1Permissions.transferResourceOwnership(folder.getResourceId(), unknownUser,
        RevisionPrecondition.any()), CedarErrorType.NOT_FOUND, CedarErrorKey.USER_NOT_FOUND);
    assertRefused(user1Permissions.transferResourceOwnership(folder.getResourceId(), user1.getResourceId(),
        RevisionPrecondition.any()), CedarErrorType.INVALID_ARGUMENT, CedarErrorKey.INVALID_INPUT);
    RevisionConflictException conflict = Assertions.assertThrows(RevisionConflictException.class,
        () -> user1Permissions.transferResourceOwnership(folder.getResourceId(), user2.getResourceId(),
            RevisionPrecondition.exact(revision + 1)));
    Assertions.assertEquals(revision, conflict.getCurrentRevision());

    VersionedResourcePermissions after = user1Permissions.getVersionedResourcePermissions(folder.getResourceId());
    Assertions.assertEquals(user1.getId(), after.content().getOwner().getId());
    Assertions.assertEquals(revision, after.revision(), "A refused transfer must not advance the ACL revision");
    Assertions.assertEquals(user1.getId(), foldersOf(user1Context).findFolderById(folder.getResourceId()).getOwnedBy());
  }

  /**
   * A transfer moves the OWNS relation and the ownedBy property together. The new owner's direct
   * grant becomes redundant and is removed, and every other grant is kept.
   */
  @Test
  public void resourceOwnershipTransferHandsTheResourceToTheNewOwner() {
    FolderServerFolder folder = createFolderUnderUser1Home("Transfer Folder");
    FolderServerGroup group = CedarDataServices.getInstance().getGroupServiceSession(user1Context)
        .createGroup("ownership-transfer-grant-group", "Group whose grant survives the transfer");
    ResourcePermissionsRequest shared = requestOwnedByUser1();
    shared.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.EDITOR));
    shared.getGroupPermissions().add(new ResourcePermissionGroupPermissionPair(
        new ResourcePermissionGroup(group.getId()), ResourceRole.VIEWER));
    BackendCallResult<VersionedResourcePermissions> granted = permissionsOf(user1Context)
        .updateResourcePermissions(folder.getResourceId(), shared, RevisionPrecondition.any());
    Assertions.assertFalse(granted.isError(), () -> granted.getFirstErrorMessage());
    long revision = granted.getPayload().revision();

    BackendCallResult<VersionedResourcePermissions> transfer = permissionsOf(user1Context)
        .transferResourceOwnership(folder.getResourceId(), user2.getResourceId(), RevisionPrecondition.exact(revision));

    Assertions.assertFalse(transfer.isError(), () -> transfer.getFirstErrorMessage());
    Assertions.assertEquals(revision + 1, transfer.getPayload().revision());
    Assertions.assertEquals(user2.getId(), transfer.getPayload().content().getOwner().getId());
    Assertions.assertTrue(transfer.getPayload().content().getUserPermissions().isEmpty(),
        "The new owner's direct grant must be removed");
    Assertions.assertEquals(List.of(group.getId()), transfer.getPayload().content().getGroupPermissions().stream()
        .map(permission -> permission.getGroup().getId()).toList(), "The group's grant must survive the transfer");
    Assertions.assertEquals(user2.getId(), foldersOf(user1Context).findFolderById(folder.getResourceId()).getOwnedBy());
    Assertions.assertTrue(permissionsOf(user2Context).userIsOwnerOfResource(folder.getResourceId()));
    Assertions.assertFalse(permissionsOf(user1Context).userIsOwnerOfResource(folder.getResourceId()));
  }

  /**
   * The root, the Users folder and every home folder hold the tree together, so nobody may delete,
   * move, share, publish or give one away. That covers the home folder's owner and the built-in
   * administrator, whose override reaches every other folder. The session recognizes such a folder
   * from the flags stored on its node, and everything else the owner may do in it remains.
   */
  @Test
  public void structuralFoldersWithholdDeleteMoveSharingAndTransferFromEveryone() throws Exception {
    Set<ResourceCapability> structural = EnumSet.of(ResourceCapability.DELETE_RESOURCE,
        ResourceCapability.MOVE_RESOURCE, ResourceCapability.MANAGE_GRANTS, ResourceCapability.MANAGE_OPENVIEW,
        ResourceCapability.TRANSFER_OWNERSHIP);

    Set<ResourceCapability> home = permissionsOf(user1Context).getResourceCapabilities(user1HomeId);
    Assertions.assertTrue(home.containsAll(EnumSet.of(ResourceCapability.READ_RESOURCE,
        ResourceCapability.LIST_FOLDER_CONTENTS, ResourceCapability.UPDATE_RESOURCE,
        ResourceCapability.CREATE_IN_FOLDER)), home.toString());
    Assertions.assertTrue(Collections.disjoint(home, structural), home.toString());

    CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));
    for (String path : List.of(cedarConfig.getFolderStructureConfig().getRootFolder().getPath(),
        cedarConfig.getFolderStructureConfig().getUsersFolder().getPath())) {
      FolderServerFolder folder = foldersOf(adminContext).findFolderByPath(path);
      Assertions.assertNotNull(folder, path);
      Set<ResourceCapability> capabilities = permissionsOf(adminContext).getResourceCapabilities(folder.getResourceId());
      Assertions.assertTrue(capabilities.contains(ResourceCapability.CREATE_IN_FOLDER), path + " " + capabilities);
      Assertions.assertTrue(Collections.disjoint(capabilities, structural), path + " " + capabilities);
    }

    FolderServerFolder ordinary = createFolderUnderUser1Home("Structural Contrast Folder");
    Set<ResourceCapability> overridden = permissionsOf(adminContext).getResourceCapabilities(ordinary.getResourceId());
    Assertions.assertTrue(overridden.containsAll(EnumSet.of(ResourceCapability.DELETE_RESOURCE,
        ResourceCapability.MOVE_RESOURCE)), overridden.toString());

    CedarFolderId missingFolder = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    Assertions.assertEquals(Set.of(), permissionsOf(user1Context).getResourceCapabilities(missingFolder));
  }

  /**
   * Only a group's administrators edit, delete or restaff it. The session refuses a member who is not
   * one even when no HTTP check stands in front of it, and the group is left as it was. The caller's
   * membership flags are read from the graph, so the built-in administrator, who neither belongs to nor
   * administers the group, sees two false flags while their override still lets them edit it.
   */
  @Test
  public void onlyAGroupsAdministratorsMayChangeIt() throws Exception {
    GroupServiceSession user1Groups = CedarDataServices.getInstance().getGroupServiceSession(user1Context);
    GroupServiceSession user2Groups = CedarDataServices.getInstance().getGroupServiceSession(user2Context);
    GroupServiceSession adminGroups = CedarDataServices.getInstance().getGroupServiceSession(
        CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig)));
    FolderServerGroup group = user1Groups.createGroup("administrators-only-group", "before");
    CedarGroupUsersRequest membership = new CedarGroupUsersRequest();
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user1.getId()), true, true));
    membership.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), false, true));
    Assertions.assertFalse(user1Groups.updateGroupUsers(group.getResourceId(), membership).isError());

    Assertions.assertEquals(new GroupCallerMembership(true, true), user1Groups.findCallerMembership(group.getResourceId()));
    Assertions.assertEquals(new GroupCallerMembership(true, false), user2Groups.findCallerMembership(group.getResourceId()));
    Assertions.assertEquals(new GroupCallerMembership(false, false), adminGroups.findCallerMembership(group.getResourceId()));
    Assertions.assertNull(user1Groups.findCallerMembership(CedarGroupId.build(
        cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.GROUP))), "a missing group has no membership");

    Assertions.assertNull(user2Groups.updateGroupById(group.getResourceId(), Map.of(NodeProperty.DESCRIPTION, "by a member")));
    Assertions.assertNull(user2Groups.updateGroupById(group.getResourceId(), Map.of(NodeProperty.DESCRIPTION, "by a member"),
        RevisionPrecondition.any()));
    Assertions.assertFalse(user2Groups.deleteGroupById(group.getResourceId()));
    Assertions.assertFalse(user2Groups.deleteGroupById(group.getResourceId(), RevisionPrecondition.any()));
    CedarGroupUsersRequest takeover = new CedarGroupUsersRequest();
    takeover.getUsers().add(new CedarGroupUserRequest(new ResourcePermissionUser(user2.getId()), true, true));
    BackendCallResult<VersionedGroupUsers> refused = user2Groups.updateGroupUsers(group.getResourceId(), takeover);
    assertRefused(refused, CedarErrorType.PERMISSION, CedarErrorKey.GROUP_CAN_BY_MODIFIED_ONLY_BY_GROUP_ADMIN);

    Assertions.assertEquals("before", user1Groups.findGroupById(group.getResourceId()).getDescription());
    Assertions.assertEquals(new GroupCallerMembership(true, true), user1Groups.findCallerMembership(group.getResourceId()),
        "the refused takeover must leave the administrator in place");
    Assertions.assertEquals(2, user1Groups.findGroupUsers(group.getResourceId()).getUsers().size());

    Assertions.assertNotNull(adminGroups.updateGroupById(group.getResourceId(),
        Map.of(NodeProperty.DESCRIPTION, "by the built-in administrator")));
    Assertions.assertEquals("by the built-in administrator",
        user1Groups.findGroupById(group.getResourceId()).getDescription());
  }

  private static FolderServerArtifact createInstanceOf(CedarTemplateId templateId, String name) {
    FolderServerInstance instance = new FolderServerInstance();
    instance.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.INSTANCE));
    instance.setName(name);
    instance.setDescription("Created by WorkspacePermissionIntegrationTest");
    instance.setIsBasedOn(templateId);
    FolderServerArtifact created = foldersOf(user1Context).createResourceAsChildOfId(instance, user1HomeId);
    Assertions.assertNotNull(created, "The instance '" + name + "' should be created");
    return created;
  }

  private static Set<String> instancesOf(CedarRequestContext context, CedarTemplateId templateId, int limit, int offset) {
    return foldersOf(context).searchIsBasedOn(List.of(CedarResourceType.INSTANCE), templateId, limit, offset,
            List.of(QuerySortOptions.DEFAULT_SORT_FIELD.getName())).stream()
        .map(FolderServerResourceExtract::getId)
        .collect(Collectors.toSet());
  }

  /**
   * The listing of a template's instances shows each caller the instances they may read, and the
   * built-in administrator all of them. Copying instances to a new template version runs as that
   * administrator and pages through this listing, so a listing that hid instances from it would
   * leave them behind without a word.
   */
  @Test
  public void aTemplatesInstancesAreListedForTheirReadersAndAllForTheAdministrator() throws Exception {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName("Instance Listing Template");
    template.setVersion("0.0.1");
    template.setPublicationStatus("bibo:draft");
    CedarTemplateId templateId = CedarTemplateId.build(
        foldersOf(user1Context).createResourceAsChildOfId(template, user1HomeId).getId());
    FolderServerArtifact first = createInstanceOf(templateId, "Listed Instance One");
    FolderServerArtifact second = createInstanceOf(templateId, "Listed Instance Two");
    createInstanceOf(CedarTemplateId.build(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE)),
        "Instance Of Another Template");
    CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));
    List<CedarResourceType> instances = List.of(CedarResourceType.INSTANCE);

    Assertions.assertEquals(Set.of(first.getId(), second.getId()), instancesOf(user1Context, templateId, 10, 0));
    Assertions.assertEquals(2, foldersOf(user1Context).searchIsBasedOnCount(instances, templateId));
    Assertions.assertEquals(Set.of(), instancesOf(user2Context, templateId, 10, 0));
    Assertions.assertEquals(0, foldersOf(user2Context).searchIsBasedOnCount(instances, templateId));
    Assertions.assertEquals(Set.of(first.getId(), second.getId()), instancesOf(adminContext, templateId, 10, 0),
        "the built-in administrator reads every instance without a grant");
    Assertions.assertEquals(2, foldersOf(adminContext).searchIsBasedOnCount(instances, templateId));

    Set<String> firstPage = instancesOf(user1Context, templateId, 1, 0);
    Set<String> secondPage = instancesOf(user1Context, templateId, 1, 1);
    Assertions.assertEquals(1, firstPage.size());
    Assertions.assertEquals(1, secondPage.size());
    Assertions.assertNotEquals(firstPage, secondPage, "consecutive pages must not repeat an instance");

    ResourcePermissionsRequest request = requestOwnedByUser1();
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.VIEWER));
    Assertions.assertFalse(permissionsOf(user1Context).updateResourcePermissions(first.getResourceId(), request).isError());
    Assertions.assertEquals(Set.of(first.getId()), instancesOf(user2Context, templateId, 10, 0));
    Assertions.assertEquals(1, foldersOf(user2Context).searchIsBasedOnCount(instances, templateId));
  }

  private static void assertRefused(BackendCallResult<?> result, CedarErrorType type, CedarErrorKey key) {
    Assertions.assertTrue(result.isError(), "The call should be refused with " + key);
    Assertions.assertEquals(type, result.getFirstError().getErrorPack().getErrorType(), key.toString());
    Assertions.assertEquals(key, result.getFirstError().getErrorPack().getErrorKey());
  }

}
