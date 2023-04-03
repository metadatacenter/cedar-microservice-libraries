package org.metadatacenter.model;

public enum ServerName {

  GROUP("group"),
  MESSAGING("messaging"),
  REPO("repo"),
  RESOURCE("resource"),
  SCHEMA("schema"),
  SUBMISSION("submission"),
  ARTIFACT("artifact"),
  TERMINOLOGY("terminology"),
  USER("user"),
  VALUERECOMMENDER("valuerecommender"),
  WORKER("worker"),
  OPENVIEW("openview"),
  MONITOR("monitor"),
  IMPEX("impex");

  private String name;

  ServerName(String name) {
    this.name = name;
  }

  public static ServerName forName(String server) {
    for (ServerName s : ServerName.values()) {
      if (s.getName().equals(server)) {
        return s;
      }
    }
    return null;
  }

  public String getName() {
    return name;
  }
}
