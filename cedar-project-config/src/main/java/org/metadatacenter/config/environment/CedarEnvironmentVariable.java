package org.metadatacenter.config.environment;

public enum CedarEnvironmentVariable {

  CEDAR_VERSION("CEDAR_VERSION"),
  CEDAR_VERSION_MODIFIER("CEDAR_VERSION_MODIFIER"),

  CEDAR_HOME("CEDAR_HOME"),
  KEYCLOAK_HOME("KEYCLOAK_HOME"),
  NGINX_HOME("NGINX_HOME"),

  CEDAR_PROFILE("CEDAR_PROFILE"),

  CEDAR_HOST("CEDAR_HOST"),

  CEDAR_BIOPORTAL_API_KEY("CEDAR_BIOPORTAL_API_KEY"),
  CEDAR_ANALYTICS_KEY("CEDAR_ANALYTICS_KEY"),
  CEDAR_NCBI_SRA_FTP_PASSWORD("CEDAR_NCBI_SRA_FTP_PASSWORD"),

  CEDAR_NEO4J_TRANSACTION_URL("CEDAR_NEO4J_TRANSACTION_URL"),
  CEDAR_NEO4J_AUTH_STRING("CEDAR_NEO4J_AUTH_STRING"),

  CEDAR_ADMIN_USER_PASSWORD("CEDAR_ADMIN_USER_PASSWORD"),
  CEDAR_ADMIN_USER_API_KEY("CEDAR_ADMIN_USER_API_KEY"),

  CEDAR_RESOURCE_SERVER_USER_CALLBACK_URL("CEDAR_RESOURCE_SERVER_USER_CALLBACK_URL"),
  CEDAR_RESOURCE_SERVER_ADMIN_CALLBACK_URL("CEDAR_RESOURCE_SERVER_ADMIN_CALLBACK_URL"),
  CEDAR_KEYCLOAK_CLIENT_ID("CEDAR_KEYCLOAK_CLIENT_ID"),

  CEDAR_MONGO_USER_NAME("CEDAR_MONGO_USER_NAME"),
  CEDAR_MONGO_USER_PASSWORD("CEDAR_MONGO_USER_PASSWORD"),

  CEDAR_SALT_API_KEY("CEDAR_SALT_API_KEY"),

  CEDAR_LD_USER_BASE("CEDAR_LD_USER_BASE"),

  CEDAR_EVERYBODY_GROUP_NAME("CEDAR_EVERYBODY_GROUP_NAME"),

  CEDAR_TEST_USER1_EMAIL("CEDAR_TEST_USER1_EMAIL"),
  CEDAR_TEST_USER1_PASSWORD("CEDAR_TEST_USER1_PASSWORD"),
  CEDAR_TEST_USER1_ID("CEDAR_TEST_USER1_ID"),
  CEDAR_TEST_USER1_NAME("CEDAR_TEST_USER1_NAME"),

  CEDAR_TEST_USER2_EMAIL("CEDAR_TEST_USER2_EMAIL"),
  CEDAR_TEST_USER2_PASSWORD("CEDAR_TEST_USER2_PASSWORD"),
  CEDAR_TEST_USER2_ID("CEDAR_TEST_USER2_ID"),
  CEDAR_TEST_USER2_NAME("CEDAR_TEST_USER2_NAME");

  private final String name;

  CedarEnvironmentVariable(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
