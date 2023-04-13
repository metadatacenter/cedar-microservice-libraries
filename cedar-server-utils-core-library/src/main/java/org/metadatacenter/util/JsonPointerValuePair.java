package org.metadatacenter.util;

public class JsonPointerValuePair {

  private String pointer;
  private String value;

  public JsonPointerValuePair() {
  }

  public String getPointer() {
    return pointer;
  }

  public void setPointer(String pointer) {
    this.pointer = pointer;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean hasEmptyValue() {
    return getValue() == null || getValue().trim().isEmpty();
  }
}
