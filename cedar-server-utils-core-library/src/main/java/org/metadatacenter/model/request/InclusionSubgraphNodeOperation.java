package org.metadatacenter.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum InclusionSubgraphNodeOperation {

  UPDATE("update"),
  DO_NOT_UPDATE("do-not-update");

  private final String value;

  InclusionSubgraphNodeOperation(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static InclusionSubgraphNodeOperation forValue(String value) {
    for (InclusionSubgraphNodeOperation t : values()) {
      if (t.getValue().equals(value)) {
        return t;
      }
    }
    return null;
  }
}
