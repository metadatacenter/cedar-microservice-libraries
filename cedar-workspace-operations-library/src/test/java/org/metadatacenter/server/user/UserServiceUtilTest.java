package org.metadatacenter.server.user;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.SortDirection;
import org.metadatacenter.server.security.model.user.ViewMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
