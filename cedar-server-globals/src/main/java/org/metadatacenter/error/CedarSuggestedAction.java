package org.metadatacenter.error;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CedarSuggestedAction {

  NONE("none"),
  REQUEST_ROLE("requestRole"),
  LOGOUT("logout"),
  REFRESH_TOKEN("refreshToken");

  private String value;

  CedarSuggestedAction(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
