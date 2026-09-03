package org.metadatacenter.util.test;

import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.model.SystemComponent;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.metadatacenter.config.environment.CedarEnvironmentVariable.*;

/**
 * The environment the configuration tests run against.
 *
 * <p>The map is complete: it holds a value for every variable
 * {@link CedarConfigEnvironmentDescriptor} grants to any server component, so a test can install it
 * as the whole environment instead of merging it over the process environment. That completeness is
 * what makes an unresolved value in a loaded configuration mean something. A test whose environment
 * is a partial overlay resolves whatever the surrounding shell happens to export, so the same test
 * covers different variables on a developer's machine, in a CI job, and in a bare shell.
 *
 * <p>The values are fabricated and reach nothing. Ports follow the development map (9001 upwards,
 * admin +100, stop +200) so a failure message reads like the real deployment, and hosts are the
 * loopback address. {@link #assertCoversEveryServerComponent()} fails the build when a new variable
 * enters the descriptor without a value here.
 */
public final class CedarTestEnvironment {

  private CedarTestEnvironment() {
  }

  /**
   * Returns a fresh, mutable copy of the environment. Callers own the map and may adjust it before
   * installing it.
   */
  public static Map<String, String> build() {
    Map<String, String> env = new LinkedHashMap<>();

    put(env, CEDAR_HOST, "metadatacenter.orgx");
    put(env, CEDAR_NET_GATEWAY, "127.0.0.1");
    put(env, CEDAR_KEYCLOAK_ALLOW_INSECURE_TLS, "false");

    // Every service now declares its own version, and the monitor also declares CEDAR_HOME, because
    // both are reported on /server-report and /host. A declared variable this map does not supply
    // fails assertGrantedVariablesSupplied before the configuration is even built.
    put(env, CEDAR_VERSION, "0.0.0-TEST");
    put(env, CEDAR_VERSION_MODIFIER, "-0");
    put(env, CEDAR_HOME, "/tmp/cedar-test-home");

    put(env, CEDAR_ADMIN_USER_PASSWORD, "adminPassword");
    put(env, CEDAR_ADMIN_USER_API_KEY, "1234");
    put(env, CEDAR_CADSR_ADMIN_USER_API_KEY, "5678");
    put(env, CEDAR_CADSR_ONTOLOGIES_FOLDER, "/tmp/cadsr-ontologies");
    put(env, CEDAR_SALT_API_KEY, "saltme");

    put(env, CEDAR_NEO4J_HOST, "127.0.0.1");
    put(env, CEDAR_NEO4J_BOLT_PORT, "7687");
    put(env, CEDAR_NEO4J_USER_NAME, "neo4j");
    put(env, CEDAR_NEO4J_USER_PASSWORD, "password");

    put(env, CEDAR_MONGO_HOST, "localhost");
    put(env, CEDAR_MONGO_PORT, "27017");
    put(env, CEDAR_MONGO_APP_USER_NAME, "cedarUser");
    put(env, CEDAR_MONGO_APP_USER_PASSWORD, "password");

    put(env, CEDAR_OPENSEARCH_HOST, "127.0.0.1");
    put(env, CEDAR_OPENSEARCH_REST_PORT, "9200");
    put(env, CEDAR_OPENSEARCH_TRANSPORT_PORT, "9300");

    put(env, CEDAR_REDIS_PERSISTENT_HOST, "127.0.0.1");
    put(env, CEDAR_REDIS_PERSISTENT_PORT, "6379");

    put(env, CEDAR_MESSAGING_MYSQL_HOST, "127.0.0.1");
    put(env, CEDAR_MESSAGING_MYSQL_PORT, "3306");
    put(env, CEDAR_MESSAGING_MYSQL_DB, "cedar_messaging");
    put(env, CEDAR_MESSAGING_MYSQL_USER, "cedarMySQLMessagingUser");
    put(env, CEDAR_MESSAGING_MYSQL_PASSWORD, "password");

    put(env, CEDAR_LOG_MYSQL_HOST, "127.0.0.1");
    put(env, CEDAR_LOG_MYSQL_PORT, "3306");
    put(env, CEDAR_LOG_MYSQL_DB, "cedar_log");
    put(env, CEDAR_LOG_MYSQL_USER, "cedar_log_user");
    put(env, CEDAR_LOG_MYSQL_PASSWORD, "cedar_log_password");

    // The terminology server's BioPortal credentials. The suites that call BioPortal live are
    // tagged and excluded by default; these only have to substitute.
    put(env, CEDAR_BIOPORTAL_REST_BASE, "https://data.bioontology.org/");
    put(env, CEDAR_BIOPORTAL_API_KEY, "bioportalApiKey");

    put(env, CEDAR_NCBI_SRA_FTP_HOST, "ftpHost");
    put(env, CEDAR_NCBI_SRA_FTP_USER, "ftpUser");
    put(env, CEDAR_NCBI_SRA_FTP_PASSWORD, "ftpPassword");
    put(env, CEDAR_NCBI_SRA_FTP_DIRECTORY, "ftpDirectory");
    put(env, CEDAR_IMMPORT_SUBMISSION_USER, "submissionUser");
    put(env, CEDAR_IMMPORT_SUBMISSION_PASSWORD, "submissionPassword");
    put(env, CEDAR_SUBMISSION_TEMPLATE_ID_1, "http://template-id-1");
    put(env, CEDAR_SUBMISSION_TEMPLATE_ID_2, "http://template-id-2");

    put(env, CEDAR_ROR_API_PREFIX, "https://api.ror.org/v2/");
    put(env, CEDAR_ORCID_TOKEN_PREFIX, "https://orcid.org/");
    put(env, CEDAR_ORCID_API_PREFIX, "https://pub.orcid.org/");
    put(env, CEDAR_ORCID_API_CLIENT_ID, "orcidClientId");
    put(env, CEDAR_ORCID_API_CLIENT_SECRET, "orcidClientSecret");
    put(env, CEDAR_COMP_TOX_API_PREFIX, "https://api-ccte.epa.gov/");
    put(env, CEDAR_COMP_TOX_API_KEY, "compToxApiKey");
    put(env, CEDAR_RRID_API_KEY, "rridApiKey");
    put(env, CEDAR_PUBMED_API_KEY, "pubmedApiKey");

    put(env, CEDAR_DATACITE_ENABLED, "false");
    put(env, CEDAR_DATACITE_REPOSITORY_ID, "dataCiteRepositoryId");
    put(env, CEDAR_DATACITE_REPOSITORY_PASSWORD, "dataCiteRepositoryPassword");
    put(env, CEDAR_DATACITE_REPOSITORY_PREFIX, "10.5072");
    put(env, CEDAR_DATACITE_API_ENDPOINT_URL, "https://api.test.datacite.org/dois");
    put(env, CEDAR_DATACITE_TEMPLATE_ID, "http://datacite-template-id");

    put(env, CEDAR_TEST_USER1_ID, "https://metadatacenter.org/users/11111111-2222-3333-4444-555555555555");
    put(env, CEDAR_TEST_USER2_ID, "https://metadatacenter.org/users/66666666-7777-8888-9999-000000000000");

    // The backslashes belong to the value, as they do in the shell profile and the CI workflows.
    // The variable substitutes into a double-quoted YAML scalar, so a bare quote would end the
    // scalar and leave the file unparseable; the escape survives quoting and the YAML parser hands
    // TrustedFoldersConfig the plain JSON it parses into the folder map.
    put(env, CEDAR_TRUSTED_FOLDERS,
        "{\\\"caDSR\\\":[\\\"https://repo.metadatacenter.orgx/folders/00000000-1111-2222-3333-444444444444\\\"]}");

    putServer(env, CEDAR_ARTIFACT_SERVER_HOST, CEDAR_ARTIFACT_HTTP_PORT, CEDAR_ARTIFACT_ADMIN_PORT,
        CEDAR_ARTIFACT_STOP_PORT, 9001);
    putServer(env, CEDAR_REPO_SERVER_HOST, CEDAR_REPO_HTTP_PORT, CEDAR_REPO_ADMIN_PORT,
        CEDAR_REPO_STOP_PORT, 9002);
    putServer(env, CEDAR_SCHEMA_SERVER_HOST, CEDAR_SCHEMA_HTTP_PORT, CEDAR_SCHEMA_ADMIN_PORT,
        CEDAR_SCHEMA_STOP_PORT, 9003);
    putServer(env, CEDAR_TERMINOLOGY_SERVER_HOST, CEDAR_TERMINOLOGY_HTTP_PORT, CEDAR_TERMINOLOGY_ADMIN_PORT,
        CEDAR_TERMINOLOGY_STOP_PORT, 9004);
    putServer(env, CEDAR_USER_SERVER_HOST, CEDAR_USER_HTTP_PORT, CEDAR_USER_ADMIN_PORT,
        CEDAR_USER_STOP_PORT, 9005);
    putServer(env, CEDAR_VALUERECOMMENDER_SERVER_HOST, CEDAR_VALUERECOMMENDER_HTTP_PORT,
        CEDAR_VALUERECOMMENDER_ADMIN_PORT, CEDAR_VALUERECOMMENDER_STOP_PORT, 9006);
    putServer(env, CEDAR_RESOURCE_SERVER_HOST, CEDAR_RESOURCE_HTTP_PORT, CEDAR_RESOURCE_ADMIN_PORT,
        CEDAR_RESOURCE_STOP_PORT, 9007);
    putServer(env, CEDAR_IMPEX_SERVER_HOST, CEDAR_IMPEX_HTTP_PORT, CEDAR_IMPEX_ADMIN_PORT,
        CEDAR_IMPEX_STOP_PORT, 9008);
    putServer(env, CEDAR_GROUP_SERVER_HOST, CEDAR_GROUP_HTTP_PORT, CEDAR_GROUP_ADMIN_PORT,
        CEDAR_GROUP_STOP_PORT, 9009);
    putServer(env, CEDAR_SUBMISSION_SERVER_HOST, CEDAR_SUBMISSION_HTTP_PORT, CEDAR_SUBMISSION_ADMIN_PORT,
        CEDAR_SUBMISSION_STOP_PORT, 9010);
    putServer(env, CEDAR_WORKER_SERVER_HOST, CEDAR_WORKER_HTTP_PORT, CEDAR_WORKER_ADMIN_PORT,
        CEDAR_WORKER_STOP_PORT, 9011);
    putServer(env, CEDAR_MESSAGING_SERVER_HOST, CEDAR_MESSAGING_HTTP_PORT, CEDAR_MESSAGING_ADMIN_PORT,
        CEDAR_MESSAGING_STOP_PORT, 9012);
    putServer(env, CEDAR_OPENVIEW_SERVER_HOST, CEDAR_OPENVIEW_HTTP_PORT, CEDAR_OPENVIEW_ADMIN_PORT,
        CEDAR_OPENVIEW_STOP_PORT, 9013);
    putServer(env, CEDAR_MONITOR_SERVER_HOST, CEDAR_MONITOR_HTTP_PORT, CEDAR_MONITOR_ADMIN_PORT,
        CEDAR_MONITOR_STOP_PORT, 9014);
    putServer(env, CEDAR_BRIDGE_SERVER_HOST, CEDAR_BRIDGE_HTTP_PORT, CEDAR_BRIDGE_ADMIN_PORT,
        CEDAR_BRIDGE_STOP_PORT, 9015);

    return env;
  }

  /**
   * Fails with the names of the variables missing from {@link #build()}, if any server component is
   * granted a variable this environment does not supply. Two kinds are exempt. A boolean, because the
   * variable provider defaults an absent one to {@code false}, so its absence resolves rather than
   * leaving a placeholder behind. And an optional variable, because the component that declares it
   * carries its own default and is expected to run without it — the worker's fifteen log aggregation
   * settings are all of this kind.
   */
  public static void assertCoversEveryServerComponent() {
    Map<String, String> env = build();
    StringBuilder missing = new StringBuilder();
    for (SystemComponent component : SystemComponent.values()) {
      if (component.getServerName() == null) {
        continue;
      }
      for (CedarEnvironmentVariable variable : CedarConfigEnvironmentDescriptor.getVariableNamesFor(component)) {
        if (!variable.isBoolean() && !variable.isOptional() && !env.containsKey(variable.getName())) {
          missing.append("\n  ").append(variable.getName()).append(" (granted to ").append(component).append(")");
        }
      }
    }
    if (missing.length() > 0) {
      throw new AssertionError("CedarTestEnvironment supplies no value for:" + missing);
    }
  }

  private static void putServer(Map<String, String> env, CedarEnvironmentVariable host,
                                CedarEnvironmentVariable httpPort, CedarEnvironmentVariable adminPort,
                                CedarEnvironmentVariable stopPort, int basePort) {
    put(env, host, "127.0.0.1");
    put(env, httpPort, String.valueOf(basePort));
    put(env, adminPort, String.valueOf(basePort + 100));
    put(env, stopPort, String.valueOf(basePort + 200));
  }

  private static void put(Map<String, String> env, CedarEnvironmentVariable variable, String value) {
    env.put(variable.getName(), value);
  }

}
