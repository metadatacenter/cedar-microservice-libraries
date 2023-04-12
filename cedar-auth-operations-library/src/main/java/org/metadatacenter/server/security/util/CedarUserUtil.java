package org.metadatacenter.server.security.util;

import org.apache.commons.codec.binary.Hex;
import org.metadatacenter.config.BlueprintUIPreferences;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CedarUserUtil {

  private static final Logger log = LoggerFactory.getLogger(CedarUserUtil.class);

  private CedarUserUtil() {
  }

  public static CedarUser createUserFromBlueprint(BlueprintUserProfile blueprintProfile, CedarUserRepresentation ur, CedarSuperRole superRole,
                                                  CedarConfig cedarConfig, String username) {
    BlueprintUIPreferences uiPref = blueprintProfile.getUiPreferences();

    CedarUser user = new CedarUser();
    user.setId(ur.getId());
    user.setFirstName(ur.getFirstName());
    user.setLastName(ur.getLastName());
    user.setEmail(ur.getEmail());

    LocalDateTime now = LocalDateTime.now();
    // create a default API Key
    CedarUserApiKey apiKeyObject = new CedarUserApiKey();
    String adminUserName = cedarConfig.getAdminUserConfig().getUserName();
    String caDSRAdminUserName = cedarConfig.getCaDSRAdminUserConfig().getUserName();
    if (adminUserName.equals(username)) {
      apiKeyObject.setKey(cedarConfig.getAdminUserConfig().getApiKey());
    } else if (caDSRAdminUserName.equals(username)) {
      apiKeyObject.setKey(cedarConfig.getCaDSRAdminUserConfig().getApiKey());
    } else {
      apiKeyObject.setKey(generateApiKey(blueprintProfile.getDefaultAPIKey().getSalt(), (ur.getId())));
    }
    apiKeyObject.setCreationDate(now);
    apiKeyObject.setEnabled(true);
    apiKeyObject.setServiceName(blueprintProfile.getDefaultAPIKey().getServiceName());
    apiKeyObject.setDescription(blueprintProfile.getDefaultAPIKey().getDescription());

    user.getApiKeys().add(apiKeyObject);

    List<CedarUserRole> roles = CedarUserUtil.getRolesForType(blueprintProfile, superRole);
    user.getRoles().addAll(roles);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    // set folder view defaults
    CedarUserUIFolderView folderView = user.getUiPreferences().getFolderView();
    folderView.setSortBy(uiPref.getFolderView().getSortBy());
    folderView.setSortDirection(SortDirection.forValue(uiPref.getFolderView().getSortDirection()));
    folderView.setViewMode(ViewMode.forValue(uiPref.getFolderView().getViewMode()));

    // set resource type filter defaults
    CedarUserUIResourceTypeFilters resourceTypeFilters = user.getUiPreferences().getResourceTypeFilters();
    resourceTypeFilters.setField(true);
    resourceTypeFilters.setElement(true);
    resourceTypeFilters.setTemplate(true);
    resourceTypeFilters.setInstance(true);

    CedarUserUIResourceVersionFilter resourceVersionFilter = user.getUiPreferences().getResourceVersionFilter();
    resourceVersionFilter.setVersion(ResourceVersionFilter.ALL);

    CedarUserUIResourcePublicationStatusFilter resourceStatusFilter = user.getUiPreferences().getResourcePublicationStatusFilter();
    resourceStatusFilter.setPublicationStatus(ResourcePublicationStatusFilter.ALL);

    CedarUserUIInfoPanel infoPanel = user.getUiPreferences().getInfoPanel();
    infoPanel.setOpened(false);
    infoPanel.setActiveTab(CedarUserInfoPanelTab.INFO);

    CedarUserUITemplateEditor templateEditor = user.getUiPreferences().getTemplateEditor();
    templateEditor.setTemplateJsonViewer(false);

    CedarUserUIMetadataEditor metadataEditor = user.getUiPreferences().getMetadataEditor();
    metadataEditor.setTemplateJsonViewer(false);
    metadataEditor.setMetadataJsonViewer(false);

    user.getUiPreferences().setStylesheet(blueprintProfile.getUiPreferences().getStylesheet());

    return user;
  }

  public static List<CedarUserRole> getRolesForType(BlueprintUserProfile blueprintProfile, CedarSuperRole superRole) {
    List<CedarUserRole> roles = new ArrayList<>();
    Map<CedarSuperRole, List<CedarUserRole>> defaultRoles = blueprintProfile.getDefaultRoles();
    if (defaultRoles != null) {
      List<CedarUserRole> roleList = defaultRoles.get(superRole);
      if (roleList != null) {
        roles.addAll(roleList);
      }
    }
    if (roles.isEmpty()) {
      return null;
    } else {
      return roles;
    }
  }

  private static String generateApiKey(String salt, String userId) {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      log.error("Error while building SHA-256 digest");
    }
    digest.update(salt.getBytes(StandardCharsets.UTF_8));
    byte[] hash = digest.digest(userId.getBytes(StandardCharsets.UTF_8));

    for (int i = 0; i < 1000; i++) {
      hash = digest.digest(hash);
    }
    char[] chars = Hex.encodeHex(hash);
    String key = new String(chars);
    return key;
  }
}
