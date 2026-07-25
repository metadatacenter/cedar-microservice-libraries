package org.metadatacenter.util.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared configuration-load test for the CEDAR microservices.
 *
 * <p>Each server subclasses this once, names its {@link SystemComponent}, and inherits the test
 * that builds the component's environment map and instantiates {@link CedarConfig} from it.
 * The environment set here is the union of the variables the per-server tests used to set
 * individually; {@link CedarEnvironmentVariableProvider#getFor} narrows it to the variables the
 * component declares, so the surplus is inert. {@link TestUtil#setEnv} merges into the process
 * environment rather than replacing it, matching the behavior of the tests this class replaces.
 *
 * <p>Subclasses can adjust the environment before it is applied via
 * {@link #customizeEnvironment(Map)}, and add server-specific assertions on the loaded
 * configuration via {@link #assertServerSpecificConfig(CedarConfig)}.
 */
public abstract class AbstractCedarConfigTest {

  protected abstract SystemComponent getSystemComponent();

  protected void customizeEnvironment(Map<String, String> env) {
  }

  protected void assertServerSpecificConfig(CedarConfig config) {
  }

  @Before
  public void setEnvironment() {
    Map<String, String> env = new HashMap<>();

    env.put(CedarEnvironmentVariable.CEDAR_HOST.getName(), "metadatacenter.orgx");
    env.put(CedarEnvironmentVariable.CEDAR_NET_GATEWAY.getName(), "127.0.0.1");

    env.put(CedarEnvironmentVariable.CEDAR_ADMIN_USER_API_KEY.getName(), "1234");
    env.put(CedarEnvironmentVariable.CEDAR_ADMIN_USER_PASSWORD.getName(), "adminPassword");

    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_NAME.getName(), "neo4j");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_PASSWORD.getName(), "password");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_BOLT_PORT.getName(), "7687");

    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_NAME.getName(), "cedarUser");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_PASSWORD.getName(), "password");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_HOST.getName(), "localhost");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_PORT.getName(), "27017");

    env.put(CedarEnvironmentVariable.CEDAR_REDIS_PERSISTENT_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_REDIS_PERSISTENT_PORT.getName(), "6379");

    env.put(CedarEnvironmentVariable.CEDAR_OPENSEARCH_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_OPENSEARCH_REST_PORT.getName(), "9200");
    env.put(CedarEnvironmentVariable.CEDAR_OPENSEARCH_TRANSPORT_PORT.getName(), "9300");

    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PORT.getName(), "3306");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_DB.getName(), "cedar_log");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_USER.getName(), "cedar_log_user");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PASSWORD.getName(), "cedar_log_password");

    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PORT.getName(), "3306");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_DB.getName(), "cedar_messaging");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_USER.getName(), "cedarMySQLMessagingUser");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PASSWORD.getName(), "password");

    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_HOST.getName(), "ftpHost");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_USER.getName(), "ftpUser");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_PASSWORD.getName(), "ftpPassword");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_DIRECTORY.getName(), "ftpDirectory");

    env.put(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_USER.getName(), "submissionUser");
    env.put(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_PASSWORD.getName(), "submissionPassword");

    env.put(CedarEnvironmentVariable.CEDAR_SALT_API_KEY.getName(), "saltme");
    env.put(CedarEnvironmentVariable.CEDAR_VALIDATION_ENABLED.getName(), "true");
    env.put(CedarEnvironmentVariable.CEDAR_CADSR_ONTOLOGIES_FOLDER.getName(), "/tmp/cadsr-ontologies");
    env.put(CedarEnvironmentVariable.CEDAR_TRUSTED_FOLDERS.getName(),
        "{\\\"caDSR\\\":[\\\"https://repo.metadatacenter.orgx/folders/c3a7b03c-87bb-49c4-b311-2eb1bd398c4e\\\"]}");

    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_1.getName(), "http://template-id-1");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_2.getName(), "http://template-id-2");

    env.put(CedarEnvironmentVariable.CEDAR_TEST_USER1_ID.getName(), "https://metadatacenter.orgx/users/test-user-1");
    env.put(CedarEnvironmentVariable.CEDAR_TEST_USER2_ID.getName(), "https://metadatacenter.orgx/users/test-user-2");

    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_SERVER_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_USER_SERVER_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_SERVER_HOST.getName(), "127.0.0.1");

    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_HTTP_PORT.getName(), "9001");
    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_ADMIN_PORT.getName(), "9101");
    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_STOP_PORT.getName(), "9201");

    env.put(CedarEnvironmentVariable.CEDAR_REPO_HTTP_PORT.getName(), "9002");
    env.put(CedarEnvironmentVariable.CEDAR_REPO_ADMIN_PORT.getName(), "9102");
    env.put(CedarEnvironmentVariable.CEDAR_REPO_STOP_PORT.getName(), "9202");

    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_HTTP_PORT.getName(), "9003");
    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_ADMIN_PORT.getName(), "9103");
    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_STOP_PORT.getName(), "9203");

    env.put(CedarEnvironmentVariable.CEDAR_USER_HTTP_PORT.getName(), "9005");
    env.put(CedarEnvironmentVariable.CEDAR_USER_ADMIN_PORT.getName(), "9105");
    env.put(CedarEnvironmentVariable.CEDAR_USER_STOP_PORT.getName(), "9205");

    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_HTTP_PORT.getName(), "9006");
    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_ADMIN_PORT.getName(), "9106");
    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_STOP_PORT.getName(), "9206");

    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_HTTP_PORT.getName(), "9007");
    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_ADMIN_PORT.getName(), "9107");
    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_STOP_PORT.getName(), "9207");

    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_HTTP_PORT.getName(), "9008");
    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_ADMIN_PORT.getName(), "9108");
    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_STOP_PORT.getName(), "9208");

    env.put(CedarEnvironmentVariable.CEDAR_GROUP_HTTP_PORT.getName(), "9009");
    env.put(CedarEnvironmentVariable.CEDAR_GROUP_ADMIN_PORT.getName(), "9109");
    env.put(CedarEnvironmentVariable.CEDAR_GROUP_STOP_PORT.getName(), "9209");

    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_HTTP_PORT.getName(), "9010");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_ADMIN_PORT.getName(), "9110");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_STOP_PORT.getName(), "9210");

    env.put(CedarEnvironmentVariable.CEDAR_WORKER_HTTP_PORT.getName(), "9011");
    env.put(CedarEnvironmentVariable.CEDAR_WORKER_ADMIN_PORT.getName(), "9111");
    env.put(CedarEnvironmentVariable.CEDAR_WORKER_STOP_PORT.getName(), "9211");

    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_HTTP_PORT.getName(), "9012");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_ADMIN_PORT.getName(), "9112");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_STOP_PORT.getName(), "9212");

    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_HTTP_PORT.getName(), "9013");
    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_ADMIN_PORT.getName(), "9113");
    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_STOP_PORT.getName(), "9213");

    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_HTTP_PORT.getName(), "9014");
    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_ADMIN_PORT.getName(), "9114");
    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_STOP_PORT.getName(), "9214");

    customizeEnvironment(env);

    TestUtil.setEnv(env);
  }

  @Test
  public void testGetInstance() throws Exception {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(getSystemComponent());
    CedarConfig instance = CedarConfig.getInstance(environment);
    Assert.assertNotNull(instance);
    assertServerSpecificConfig(instance);
  }

}
