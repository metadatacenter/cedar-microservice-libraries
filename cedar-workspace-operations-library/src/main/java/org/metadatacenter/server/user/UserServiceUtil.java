package org.metadatacenter.server.user;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserUIFolderView;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.util.json.JsonMapper;

import java.util.Map;

public abstract class UserServiceUtil {

  public static CedarUser validateModifications(CedarUser cedarUser, Map<String, Object> modificationsMap) {
    JsonNode userNode = JsonMapper.MAPPER.valueToTree(cedarUser);
    for (String k : modificationsMap.keySet()) {
      String pointerS = "/" + k.replace(".", "/");
      pointerS.replaceAll("//", "/");
      if (!pointerS.startsWith("/uiPreferences/")) {
        return null;
      }
      JsonPointer pointer = JsonPointer.compile(pointerS);
      JsonNode v = userNode.at(pointer);
      if (!v.isMissingNode()) {
        JsonNode newValue = JsonMapper.MAPPER.valueToTree(modificationsMap.get(k));
        JsonNode parentNode = userNode.at(pointer.head());
        String lastNodeName = pointer.last().toString().replace("/", "");
        ((ObjectNode) parentNode).set(lastNodeName, newValue);
      } else {
        return null;
      }
    }
    CedarUser modifiedUser = null;
    try {
      modifiedUser = JsonMapper.MAPPER.convertValue(userNode, CedarUser.class);
      if (!userUIPreferencesAreNotNull(modifiedUser)) {
        return null;
      }
    } catch (Exception e) {
      // DO NOTHING, it means the modifications render the user invalid.
    }
    return modifiedUser;
  }


  private static boolean userUIPreferencesAreNotNull(CedarUser user) {
    CedarUserUIPreferences uiPreferences = user.getUiPreferences();
    if (uiPreferences == null) {
      return false;
    }
    if (uiPreferences.getStylesheet() == null) {
      return false;
    }
    if (uiPreferences.getTemplateEditor() == null) {
      return false;
    }
    if (uiPreferences.getMetadataEditor() == null) {
      return false;
    }
    if (uiPreferences.getInfoPanel() == null) {
      return false;
    }
    if (uiPreferences.getResourceTypeFilters() == null) {
      return false;
    }
    if (uiPreferences.getResourceVersionFilter() == null) {
      return false;
    }
    if (uiPreferences.getResourcePublicationStatusFilter() == null) {
      return false;
    }
    if (uiPreferences.getFolderView() == null) {
      return false;
    } else {
      CedarUserUIFolderView folderView = uiPreferences.getFolderView();
      return folderView.getSortBy() != null && folderView.getSortDirection() != null &&
          folderView.getViewMode() != null;
    }
  }
}
