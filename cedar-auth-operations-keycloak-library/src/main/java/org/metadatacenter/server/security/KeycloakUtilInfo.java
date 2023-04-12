package org.metadatacenter.server.security;

public class KeycloakUtilInfo {

  protected String keycloakBaseURI;
  protected String keycloakRealmName;
  protected String keycloakClientId;
  protected String cedarAdminUserName;
  protected String cedarAdminUserPassword;
  protected String cedarAdminUserApiKey;

  public String getKeycloakBaseURI() {
    return keycloakBaseURI;
  }

  public void setKeycloakBaseURI(String keycloakBaseURI) {
    this.keycloakBaseURI = keycloakBaseURI;
  }

  public String getKeycloakRealmName() {
    return keycloakRealmName;
  }

  public void setKeycloakRealmName(String keycloakRealmName) {
    this.keycloakRealmName = keycloakRealmName;
  }

  public String getKeycloakClientId() {
    return keycloakClientId;
  }

  public void setKeycloakClientId(String keycloakClientId) {
    this.keycloakClientId = keycloakClientId;
  }

  public String getCedarAdminUserName() {
    return cedarAdminUserName;
  }

  public void setCedarAdminUserName(String cedarAdminUserName) {
    this.cedarAdminUserName = cedarAdminUserName;
  }

  public String getCedarAdminUserPassword() {
    return cedarAdminUserPassword;
  }

  public void setCedarAdminUserPassword(String cedarAdminUserPassword) {
    this.cedarAdminUserPassword = cedarAdminUserPassword;
  }

  public String getCedarAdminUserApiKey() {
    return cedarAdminUserApiKey;
  }

  public void setCedarAdminUserApiKey(String cedarAdminUserApiKey) {
    this.cedarAdminUserApiKey = cedarAdminUserApiKey;
  }
}
