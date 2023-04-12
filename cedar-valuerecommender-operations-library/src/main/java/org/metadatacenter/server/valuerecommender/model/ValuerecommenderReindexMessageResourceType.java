package org.metadatacenter.server.valuerecommender.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ValuerecommenderReindexMessageResourceType {

  TEMPLATE("template"),
  INSTANCE("instance");

  private final String value;

  ValuerecommenderReindexMessageResourceType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
