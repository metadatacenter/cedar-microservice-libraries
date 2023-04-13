package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerUser extends AbstractCedarResourceWithDates implements CedarUserRepresentation {

  private String firstName;
  private String lastName;
  private String email;
  private String homeFolderId;
  private List<String> apiKeys;
  private CedarUserApiKeyMap apiKeyMap;
  private List<CedarUserRole> roles;
  private List<String> permissions;
  private CedarUserUIPreferences uiPreferences;

  private static final Logger log = LoggerFactory.getLogger(FolderServerUser.class);

  public FolderServerUser() {
    super();
    apiKeys = new ArrayList<>();
    apiKeyMap = new CedarUserApiKeyMap();
    roles = new ArrayList<>();
    permissions = new ArrayList<>();
    uiPreferences = new CedarUserUIPreferences();
    this.setType(CedarResourceType.USER);
  }

  @Override
  @JsonIgnore
  public CedarUserId getResourceId() {
    return CedarUserId.build(getId());
  }

  @JsonProperty(NodeProperty.Label.FIRST_NAME)
  public String getFirstName() {
    return firstName;
  }

  @JsonProperty(NodeProperty.Label.FIRST_NAME)
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  @JsonProperty(NodeProperty.Label.LAST_NAME)
  public String getLastName() {
    return lastName;
  }

  @JsonProperty(NodeProperty.Label.LAST_NAME)
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  @Override
  @JsonProperty(NodeProperty.Label.EMAIL)
  public String getEmail() {
    return email;
  }

  @JsonProperty(NodeProperty.Label.EMAIL)
  public void setEmail(String email) {
    this.email = email;
  }

  @JsonProperty(NodeProperty.Label.HOME_FOLDER_ID)
  public String getHomeFolderId() {
    return homeFolderId;
  }

  @JsonProperty(NodeProperty.Label.HOME_FOLDER_ID)
  public void setHomeFolderId(String homeFolderId) {
    this.homeFolderId = homeFolderId;
  }

  @JsonProperty(NodeProperty.Label.API_KEYS)
  public List<String> getApiKeys() {
    return apiKeys;
  }

  @JsonProperty(NodeProperty.Label.API_KEYS)
  public void setApiKeys(List<String> apiKeys) {
    this.apiKeys = apiKeys;
  }

  @JsonProperty(NodeProperty.Label.API_KEY_MAP)
  public CedarUserApiKeyMap getApiKeyMap() {
    return apiKeyMap;
  }

  @JsonProperty(NodeProperty.Label.API_KEY_MAP)
  public void setApiKeyMap(CedarUserApiKeyMap apiKeyMap) {
    this.apiKeyMap = apiKeyMap;
  }

  @JsonProperty(NodeProperty.Label.ROLES)
  public List<CedarUserRole> getRoles() {
    return roles;
  }

  @JsonProperty(NodeProperty.Label.ROLES)
  public void setRoles(List<CedarUserRole> roles) {
    this.roles = roles;
  }

  @JsonProperty(NodeProperty.Label.PERMISSIONS)
  public List<String> getPermissions() {
    return permissions;
  }

  @JsonProperty(NodeProperty.Label.PERMISSIONS)
  public void setPermissions(List<String> permissions) {
    this.permissions = permissions;
  }

  @JsonProperty(NodeProperty.Label.UI_PREFERENCES)
  public CedarUserUIPreferences getUiPreferences() {
    return uiPreferences;
  }

  @JsonProperty(NodeProperty.Label.UI_PREFERENCES)
  public void setUiPreferences(CedarUserUIPreferences uiPreferences) {
    this.uiPreferences = uiPreferences;
  }

  public CedarUserExtract buildExtract() {
    return new CedarUserExtract(getId(), getFirstName(), getLastName(), getEmail());
  }

  public CedarUser buildUser() {
    CedarUser u = new CedarUser();
    u.setId(baseDataGroup.getId());
    u.setFirstName(firstName);
    u.setLastName(lastName);
    u.setEmail(email);
    u.setHomeFolderId(homeFolderId);
    u.setRoles(roles);
    u.setPermissions(permissions);
    u.setUiPreferences(uiPreferences);

    List<CedarUserApiKey> apiKeyList = new ArrayList<>();
    for (String key : apiKeyMap.keySet()) {
      apiKeyList.add(apiKeyMap.get(key));
    }
    u.setApiKeys(apiKeyList);

    return u;
  }

}
