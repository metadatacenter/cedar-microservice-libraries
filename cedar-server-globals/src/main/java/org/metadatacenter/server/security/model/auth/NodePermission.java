package org.metadatacenter.server.security.model.auth;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NodePermission {

  READ(Type.READ),
  READTHIS(Type.READTHIS),
  WRITE(Type.WRITE),
  CHANGEOWNER(Type.CHANGEOWNER);

  public static class Type {
    public static final String READ = "read";
    public static final String READTHIS = "readthis";
    public static final String WRITE = "write";
    public static final String CHANGEOWNER = "changeowner";
  }

  private final String value;

  NodePermission(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public static NodePermission forValue(String type) {
    for (NodePermission t : values()) {
      if (t.getValue().equals(type)) {
        return t;
      }
    }
    return null;
  }
}