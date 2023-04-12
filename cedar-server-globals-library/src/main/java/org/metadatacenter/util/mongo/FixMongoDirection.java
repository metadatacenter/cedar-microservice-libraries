package org.metadatacenter.util.mongo;

public enum FixMongoDirection {
  READ_FROM_MONGO(1), WRITE_TO_MONGO(2);

  private final int value;

  FixMongoDirection(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}