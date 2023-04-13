package org.metadatacenter.server.security.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KeycloakUserInfo implements IUserInfo {

  @JsonProperty
  private String sub;

  @JsonProperty
  private String name;

  private String preferredUsername;

  private String givenName;

  private String familyName;

  @JsonProperty
  private String email;

  @Override
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  @Override
  @JsonProperty("familyName")
  public String getFamilyName() {
    return familyName;
  }

  @JsonProperty("family_name")
  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  @Override
  @JsonProperty("givenName")
  public String getGivenName() {
    return givenName;
  }

  @JsonProperty("given_name")
  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  @JsonProperty("preferredUsername")
  public String getPreferredUsername() {
    return preferredUsername;
  }

  @JsonProperty("preferred_username")
  public void setPreferredUsername(String preferredUsername) {
    this.preferredUsername = preferredUsername;
  }

  @Override
  public String getSub() {
    return sub;
  }

  public void setSub(String sub) {
    this.sub = sub;
  }
}
