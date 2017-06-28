package org.metadatacenter.config.environment;

public enum CedarEnvironmentVariable {

  CEDAR_VERSION("CEDAR_VERSION"),
  CEDAR_VERSION_MODIFIER("CEDAR_VERSION_MODIFIER"),

  CEDAR_HOME("CEDAR_HOME"),
  KEYCLOAK_HOME("KEYCLOAK_HOME"),
  NGINX_HOME("NGINX_HOME"),

  CEDAR_FRONTEND_BEHAVIOR("CEDAR_FRONTEND_BEHAVIOR"),
  CEDAR_FRONTEND_TARGET("CEDAR_FRONTEND_TARGET"),
  CEDAR_HOST("CEDAR_HOST"),
  CEDAR_NET_GATEWAY("CEDAR_NET_GATEWAY"),

  CEDAR_BIOPORTAL_API_KEY("CEDAR_BIOPORTAL_API_KEY", CedarEnvironmentVariableSecure.YES),
  CEDAR_BIOPORTAL_REST_BASE("CEDAR_BIOPORTAL_REST_BASE"),

  CEDAR_ANALYTICS_KEY("CEDAR_ANALYTICS_KEY", CedarEnvironmentVariableSecure.YES),
  CEDAR_NCBI_SRA_FTP_PASSWORD("CEDAR_NCBI_SRA_FTP_PASSWORD", CedarEnvironmentVariableSecure.YES),

  CEDAR_ADMIN_USER_PASSWORD("CEDAR_ADMIN_USER_PASSWORD", CedarEnvironmentVariableSecure.YES),
  CEDAR_ADMIN_USER_API_KEY("CEDAR_ADMIN_USER_API_KEY", CedarEnvironmentVariableSecure.YES),

  CEDAR_NEO4J_HOST("CEDAR_NEO4J_HOST"),
  CEDAR_NEO4J_REST_PORT("CEDAR_NEO4J_REST_PORT"),
  CEDAR_NEO4J_USER_PASSWORD("CEDAR_NEO4J_USER_PASSWORD", CedarEnvironmentVariableSecure.YES),

  CEDAR_MONGO_APP_USER_NAME("CEDAR_MONGO_APP_USER_NAME"),
  CEDAR_MONGO_APP_USER_PASSWORD("CEDAR_MONGO_APP_USER_PASSWORD", CedarEnvironmentVariableSecure.YES),
  CEDAR_MONGO_HOST("CEDAR_MONGO_HOST"),
  CEDAR_MONGO_PORT("CEDAR_MONGO_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_ELASTICSEARCH_HOST("CEDAR_ELASTICSEARCH_HOST"),
  CEDAR_ELASTICSEARCH_TRANSPORT_PORT("CEDAR_ELASTICSEARCH_TRANSPORT_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_SALT_API_KEY("CEDAR_SALT_API_KEY", CedarEnvironmentVariableSecure.YES),

  CEDAR_TEST_USER1_ID("CEDAR_TEST_USER1_ID"),
  CEDAR_TEST_USER2_ID("CEDAR_TEST_USER2_ID"),

  CEDAR_REDIS_PERSISTENT_HOST("CEDAR_REDIS_PERSISTENT_HOST"),
  CEDAR_REDIS_PERSISTENT_PORT("CEDAR_REDIS_PERSISTENT_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_REDIS_NONPERSISTENT_HOST("CEDAR_REDIS_NONPERSISTENT_HOST"),
  CEDAR_REDIS_NONPERSISTENT_PORT("CEDAR_REDIS_NONPERSISTENT_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_FOLDER_HTTP_PORT("CEDAR_FOLDER_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_FOLDER_ADMIN_PORT("CEDAR_FOLDER_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_FOLDER_STOP_PORT("CEDAR_FOLDER_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_GROUP_HTTP_PORT("CEDAR_GROUP_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_GROUP_ADMIN_PORT("CEDAR_GROUP_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_GROUP_STOP_PORT("CEDAR_GROUP_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_REPO_HTTP_PORT("CEDAR_REPO_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_REPO_ADMIN_PORT("CEDAR_REPO_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_REPO_STOP_PORT("CEDAR_REPO_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_RESOURCE_HTTP_PORT("CEDAR_RESOURCE_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_RESOURCE_ADMIN_PORT("CEDAR_RESOURCE_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_RESOURCE_STOP_PORT("CEDAR_RESOURCE_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_SCHEMA_HTTP_PORT("CEDAR_SCHEMA_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_SCHEMA_ADMIN_PORT("CEDAR_SCHEMA_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_SCHEMA_STOP_PORT("CEDAR_SCHEMA_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_SUBMISSION_HTTP_PORT("CEDAR_SUBMISSION_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_SUBMISSION_ADMIN_PORT("CEDAR_SUBMISSION_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_SUBMISSION_STOP_PORT("CEDAR_SUBMISSION_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_TEMPLATE_HTTP_PORT("CEDAR_TEMPLATE_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_TEMPLATE_ADMIN_PORT("CEDAR_TEMPLATE_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_TEMPLATE_STOP_PORT("CEDAR_TEMPLATE_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_TERMINOLOGY_HTTP_PORT("CEDAR_TERMINOLOGY_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_TERMINOLOGY_ADMIN_PORT("CEDAR_TERMINOLOGY_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_TERMINOLOGY_STOP_PORT("CEDAR_TERMINOLOGY_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_USER_HTTP_PORT("CEDAR_USER_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_USER_ADMIN_PORT("CEDAR_USER_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_USER_STOP_PORT("CEDAR_USER_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_VALUERECOMMENDER_HTTP_PORT("CEDAR_VALUERECOMMENDER_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_VALUERECOMMENDER_ADMIN_PORT("CEDAR_VALUERECOMMENDER_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_VALUERECOMMENDER_STOP_PORT("CEDAR_VALUERECOMMENDER_STOP_PORT", CedarEnvironmentVariableNumeric.YES),

  CEDAR_WORKER_HTTP_PORT("CEDAR_WORKER_HTTP_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_WORKER_ADMIN_PORT("CEDAR_WORKER_ADMIN_PORT", CedarEnvironmentVariableNumeric.YES),
  CEDAR_WORKER_STOP_PORT("CEDAR_WORKER_STOP_PORT", CedarEnvironmentVariableNumeric.YES);

  private final String name;
  private final CedarEnvironmentVariableNumeric numeric;
  private final CedarEnvironmentVariableSecure secure;

  CedarEnvironmentVariable(String name) {
    this.name = name;
    this.numeric = CedarEnvironmentVariableNumeric.NO;
    this.secure = CedarEnvironmentVariableSecure.NO;
  }

  CedarEnvironmentVariable(String name, CedarEnvironmentVariableNumeric numeric) {
    this.name = name;
    this.numeric = numeric;
    this.secure = CedarEnvironmentVariableSecure.NO;
  }

  CedarEnvironmentVariable(String name, CedarEnvironmentVariableSecure secure) {
    this.name = name;
    this.numeric = CedarEnvironmentVariableNumeric.NO;
    this.secure = secure;
  }

  public String getName() {
    return name;
  }

  public CedarEnvironmentVariableNumeric getNumeric() {
    return numeric;
  }

  public CedarEnvironmentVariableSecure getSecure() {
    return secure;
  }

  public static CedarEnvironmentVariable forName(String name) {
    for (CedarEnvironmentVariable ev : values()) {
      if (ev.getName().equals(name)) {
        return ev;
      }
    }
    return null;
  }
}