package org.metadatacenter.server.logging.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AppLogType {

  REQUEST_FILTER("requestFilter"),
  REQUEST_HANDLER("requestHandler"),
  RESPONSE_EXCEPTION("responseException"),
  CYPHER_QUERY("cypherQuery");

  private final String value;

  AppLogType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
