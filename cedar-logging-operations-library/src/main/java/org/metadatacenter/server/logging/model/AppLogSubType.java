package org.metadatacenter.server.logging.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AppLogSubType {

  START("start"),
  END("end"),
  FULL("full");

  private final String value;

  AppLogSubType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
