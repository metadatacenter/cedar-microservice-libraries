package org.metadatacenter.model;

public enum SystemComponent {

  SERVER_GROUP(ServerName.GROUP),
  SERVER_MESSAGING(ServerName.MESSAGING),
  SERVER_REPO(ServerName.REPO),
  SERVER_RESOURCE(ServerName.RESOURCE),
  SERVER_SCHEMA(ServerName.SCHEMA),
  SERVER_SUBMISSION(ServerName.SUBMISSION),
  SERVER_ARTIFACT(ServerName.ARTIFACT),
  SERVER_TERMINOLOGY(ServerName.TERMINOLOGY),
  SERVER_USER(ServerName.USER),
  SERVER_VALUERECOMMENDER(ServerName.VALUERECOMMENDER),
  SERVER_WORKER(ServerName.WORKER),
  SERVER_OPENVIEW(ServerName.OPENVIEW),
  SERVER_MONITOR(ServerName.MONITOR),
  SERVER_IMPEX(ServerName.IMPEX),
  FRONTEND_DEVELOPMENT("frontend-development"),
  FRONTEND_TEST("frontend-test"),
  FRONTEND_PRODUCTION("frontend-production"),
  ADMIN_TOOL("admin-tool"),
  CADSR_TOOL("cadsr-tool"),
  KEYCLOAK_EVENT_LISTENER("keycloak-event-listener"), // keycloak standalone.xml
  UTIL_BIN("util-bin"), // shell scripts in cedar-util/bin
  ALL("all"); // generic case, include all variables

  private ServerName serverName;
  private String useCase;

  SystemComponent(ServerName serverName) {
    this.serverName = serverName;
  }

  SystemComponent(String useCase) {
    this.useCase = useCase;
  }

  public ServerName getServerName() {
    return serverName;
  }

  public String getStringValue() {
    if (serverName != null) {
      return serverName.getName();
    } else {
      return useCase;
    }
  }

  public static SystemComponent getFor(ServerName serverName) {
    for (SystemComponent sc : values()) {
      if (sc.getServerName() == serverName) {
        return sc;
      }
    }
    return null;
  }
}
