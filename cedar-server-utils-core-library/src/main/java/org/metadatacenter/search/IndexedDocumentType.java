package org.metadatacenter.search;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum IndexedDocumentType {

  DOC("_doc"),
  UNKNOWN("");

  private final String value;

  IndexedDocumentType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static IndexedDocumentType forValue(String value) {
    for (IndexedDocumentType t : values()) {
      if (t.getValue().equals(value)) {
        return t;
      }
    }
    return UNKNOWN;
  }
}
