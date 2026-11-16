package org.metadatacenter.search;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarUntypedFilesystemResourceId;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Every permission-filtered search matches a reader's keys against the keys an index document carries,
 * so each grant expands into the key of its own role and of every weaker one: a Manager also finds a
 * resource as an Editor and as a Viewer would. The expansion was asserted only end to end, by the
 * OpenSearch integration tests that CI alone runs.
 */
class IndexingDocumentDocumentTest {

  private static final String RESOURCE = "https://repo.metadatacenter.orgx/templates/indexed";

  @Test
  void eachGrantCarriesTheKeysOfItsRoleAndEveryWeakerOne() {
    CedarNodeMaterializedPermissions permissions = new CedarNodeMaterializedPermissions(
        CedarUntypedFilesystemResourceId.build(RESOURCE), NodeSharePermission.READ);
    permissions.setUserRole("viewer-user", ResourceRole.VIEWER);
    permissions.setUserRole("editor-user", ResourceRole.EDITOR);
    permissions.setUserRole("manager-user", ResourceRole.MANAGER);
    permissions.setGroupRole("viewer-group", ResourceRole.VIEWER);
    permissions.setGroupRole("editor-group", ResourceRole.EDITOR);
    permissions.setGroupRole("manager-group", ResourceRole.MANAGER);
    IndexingDocumentDocument document = new IndexingDocumentDocument(RESOURCE);

    document.setMaterializedPermissions(permissions);

    assertEquals(Set.of(
            "viewer-user|viewer",
            "editor-user|viewer", "editor-user|editor",
            "manager-user|viewer", "manager-user|editor", "manager-user|manager"),
        Set.copyOf(document.getUsers()));
    assertEquals(6, document.getUsers().size(), "no user key is repeated");
    assertEquals(6, document.getGroups().size(), "no group key is repeated");
    assertEquals(Set.of(
            "viewer-group|viewer",
            "editor-group|viewer", "editor-group|editor",
            "manager-group|viewer", "manager-group|editor", "manager-group|manager"),
        Set.copyOf(document.getGroups()));
    assertEquals(NodeSharePermission.READ, document.getComputedEverybodyPermission());
  }

  @Test
  void settingThePermissionsAgainReplacesTheEarlierKeys() {
    IndexingDocumentDocument document = new IndexingDocumentDocument(RESOURCE);
    CedarNodeMaterializedPermissions before = new CedarNodeMaterializedPermissions(
        CedarUntypedFilesystemResourceId.build(RESOURCE), NodeSharePermission.READ);
    before.setUserRole("former-manager", ResourceRole.MANAGER);
    before.setGroupRole("former-group", ResourceRole.VIEWER);
    document.setMaterializedPermissions(before);
    CedarNodeMaterializedPermissions after = new CedarNodeMaterializedPermissions(
        CedarUntypedFilesystemResourceId.build(RESOURCE), null);
    after.setUserRole("current-viewer", ResourceRole.VIEWER);

    document.setMaterializedPermissions(after);

    assertEquals(java.util.List.of("current-viewer|viewer"), document.getUsers(), "a revoked grant keeps no key");
    assertEquals(java.util.List.of(), document.getGroups());
    assertNull(document.getComputedEverybodyPermission(), "public read that was withdrawn is withdrawn here too");
  }
}
