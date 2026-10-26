package org.metadatacenter.server.user;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserUIWorkspace;
import org.metadatacenter.server.security.model.user.ResourceModifiedFilter;
import org.metadatacenter.server.security.model.user.ResourceSortField;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.server.security.model.user.SortDirection;
import org.metadatacenter.server.security.model.user.ViewMode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile patch names each preference by a dotted path, which becomes a JSON Pointer. A key that is
 * already a pointer would otherwise begin with an empty step and name nothing, so doubled separators
 * are collapsed first, and the preferences-only rule judges the collapsed pointer.
 */
class UserServiceUtilTest {

  @Test
  void aKeyWrittenAsAPointerNamesTheSamePreferenceAsTheDottedKey() {
    CedarUser modified = UserServiceUtil.validateModifications(provisionedUser(),
        Map.of("/uiPreferences/stylesheet", "high-contrast", "/uiPreferences/folderView/sortBy", "createdOnTS"));

    assertNotNull(modified);
    assertEquals("high-contrast", modified.getUiPreferences().getStylesheet());
    assertEquals("createdOnTS", modified.getUiPreferences().getFolderView().getSortBy());
  }

  @Test
  void aPointerOutsideThePreferencesIsStillRefused() {
    assertNull(UserServiceUtil.validateModifications(provisionedUser(), Map.of("/email", "someone-else@test.com")));
    assertNull(UserServiceUtil.validateModifications(provisionedUser(), Map.of("//permissions", "anything")));
  }

  @Test
  void theWorkspaceListingIsWrittenAsOneObject() {
    CedarUser modified = UserServiceUtil.validateModifications(provisionedUser(),
        Map.of("uiPreferences.workspace", workspaceListing(Map.of())));

    assertNotNull(modified);
    CedarUserUIWorkspace workspace = modified.getUiPreferences().getWorkspace();
    assertEquals(List.of(CedarResourceType.TEMPLATE, CedarResourceType.FIELD), workspace.getResourceTypes());
    assertEquals(ResourceModifiedFilter.CUSTOM, workspace.getModified());
    assertEquals(LocalDate.of(2026, 10, 1), workspace.getModifiedAfter());
    assertNull(workspace.getModifiedBefore());
    assertEquals(ResourceVersionFilter.LATEST, workspace.getVersion());
    assertEquals(ResourceSortField.CREATED_ON, workspace.getSortBy());
    assertEquals(SortDirection.DESC, workspace.getSortDirection());
    assertTrue(workspace.isFoldersFirst());
    assertEquals(ViewMode.LIST, workspace.getViewMode());
  }

  @Test
  void aWorkspaceListingThatDropsOrMisspellsAChoiceIsRefused() {
    Map<String, Object> cleared = new HashMap<>();
    cleared.put("uiPreferences.workspace", null);

    assertNull(UserServiceUtil.validateModifications(provisionedUser(), cleared));
    assertNull(UserServiceUtil.validateModifications(provisionedUser(),
        Map.of("uiPreferences.workspace", workspaceListing(Map.of("sortBy", "size")))));
    assertNull(UserServiceUtil.validateModifications(provisionedUser(),
        Map.of("uiPreferences.workspace", workspaceListing(Map.of("modifiedAfter", "2026-02-30")))));
    assertNull(UserServiceUtil.validateModifications(provisionedUser(),
        Map.of("uiPreferences.workspace", workspaceListing(Map.of("colour", "green")))));
    Map<String, Object> noView = workspaceListing(Map.of());
    noView.put("viewMode", null);
    assertNull(UserServiceUtil.validateModifications(provisionedUser(), Map.of("uiPreferences.workspace", noView)));
  }

  // The listing as the Workspace sends it, with some choices replaced.
  private static Map<String, Object> workspaceListing(Map<String, Object> replaced) {
    Map<String, Object> listing = new HashMap<>();
    listing.put("resourceTypes", List.of("template", "field"));
    listing.put("modified", "custom");
    listing.put("modifiedAfter", "2026-10-01");
    listing.put("modifiedBefore", null);
    listing.put("version", "latest");
    listing.put("sortBy", "createdOnTS");
    listing.put("sortDirection", "desc");
    listing.put("foldersFirst", true);
    listing.put("viewMode", "list");
    listing.putAll(replaced);
    return listing;
  }

  private static CedarUser provisionedUser() {
    CedarUser user = new CedarUser();
    user.setId("https://metadatacenter.org/users/11111111-2222-3333-4444-555555555555");
    user.setEmail("subject@test.com");
    user.getUiPreferences().setStylesheet("default");
    user.getUiPreferences().getFolderView().setSortBy("name");
    user.getUiPreferences().getFolderView().setSortDirection(SortDirection.ASC);
    user.getUiPreferences().getFolderView().setViewMode(ViewMode.GRID);
    return user;
  }
}
