package org.metadatacenter.server.valuerecommender.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ValuerecommenderReindexMessageActionType {

  CREATED("created"),
  UPDATED("updated"),
  DELETED("deleted");

  private final String value;

  ValuerecommenderReindexMessageActionType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
