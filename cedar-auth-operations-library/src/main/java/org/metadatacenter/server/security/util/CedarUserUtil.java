package org.metadatacenter.server.security.util;

import org.apache.commons.codec.binary.Hex;
import org.metadatacenter.config.BlueprintUIPreferences;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CedarUserUtil {

  private static final int API_KEY_BYTES = 32;
  private static final SecureRandom API_KEY_RANDOM = new SecureRandom();

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
    apiKeyObject.setId(UUID.randomUUID().toString());
    String adminUserName = cedarConfig.getAdminUserConfig().getUserName();
    String caDSRAdminUserName = cedarConfig.getCaDSRAdminUserConfig().getUserName();
    if (adminUserName.equals(username)) {
      apiKeyObject.setKey(cedarConfig.getAdminUserConfig().getApiKey());
    } else if (caDSRAdminUserName.equals(username)) {
      apiKeyObject.setKey(cedarConfig.getCaDSRAdminUserConfig().getApiKey());
    } else {
      apiKeyObject.setKey(generateApiKey());
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

    if (blueprintProfile.getUiPreferences().getPreferredDateFormat() != null) {
      user.getUiPreferences().setPreferredDateFormat(blueprintProfile.getUiPreferences().getPreferredDateFormat());
    }

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

  static String generateApiKey() {
    byte[] bytes = new byte[API_KEY_BYTES];
    API_KEY_RANDOM.nextBytes(bytes);
    return Hex.encodeHexString(bytes);
  }
}
