package org.metadatacenter.server.service;

public enum FieldNameInEx {
  UNDEFINED(0), INCLUDE(1), EXCLUDE(2);

  private final int value;

  FieldNameInEx(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
