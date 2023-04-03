package org.metadatacenter.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.TestUtil;

import java.util.HashMap;
import java.util.Map;

public class CedarConfigTest {

  public static final String CEDAR_ADMIN_USER_PASSWORD = "Password123";
  public static final String CEDAR_ADMIN_USER_API_KEY = "abcd-efgh";
  public static final String CEDAR_CADSR_ADMIN_USER_API_KEY = "ijkl-mnop";

  @Before
  public void setEnvironment() {
    Map<String, String> env = new HashMap<>();
    env.put(CedarEnvironmentVariable.CEDAR_VERSION.getName(), "1.0.0");
    env.put(CedarEnvironmentVariable.CEDAR_VERSION_MODIFIER.getName(), "");

    env.put(CedarEnvironmentVariable.CEDAR_HOME.getName(), "/home/cedar");
    env.put(CedarEnvironmentVariable.CEDAR_KEYCLOAK_HOME.getName(), "/home/cedar/keycloak");

    env.put(CedarEnvironmentVariable.CEDAR_NET_GATEWAY.getName(), "127.0.0.1");

    env.put(CedarEnvironmentVariable.CEDAR_FRONTEND_BEHAVIOR.getName(), "server");
    env.put(CedarEnvironmentVariable.CEDAR_FRONTEND_TARGET.getName(), "local");
    env.put(CedarEnvironmentVariable.CEDAR_HOST.getName(), "metadatacenter.orgx");

    env.put(CedarEnvironmentVariable.CEDAR_BIOPORTAL_API_KEY.getName(), "apiKey-abcd");

    env.put(CedarEnvironmentVariable.CEDAR_ANALYTICS_KEY.getName(), "false");

    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_HOST.getName(), "ftpHost");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_USER.getName(), "ftpUser");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_PASSWORD.getName(), "ftpPassword");
    env.put(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_DIRECTORY.getName(), "ftpDirectory");

    env.put(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_USER.getName(), "submissionUser");
    env.put(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_PASSWORD.getName(), "submissionPassword");

    env.put(CedarEnvironmentVariable.CEDAR_ADMIN_USER_PASSWORD.getName(), CEDAR_ADMIN_USER_PASSWORD);
    env.put(CedarEnvironmentVariable.CEDAR_ADMIN_USER_API_KEY.getName(), CEDAR_ADMIN_USER_API_KEY);

    env.put(CedarEnvironmentVariable.CEDAR_CADSR_ADMIN_USER_API_KEY.getName(), CEDAR_CADSR_ADMIN_USER_API_KEY);

    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_NAME.getName(), "neo4j");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_USER_PASSWORD.getName(), "userPassword");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_NEO4J_BOLT_PORT.getName(), "7687");

    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_NAME.getName(), "cedarUser");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_PASSWORD.getName(), "cedarPassword");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_HOST.getName(), "localhost");
    env.put(CedarEnvironmentVariable.CEDAR_MONGO_PORT.getName(), "27017");

    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PORT.getName(), "3306");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_DB.getName(), "cedar_messaging");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_USER.getName(), "cedar_messaging_user");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PASSWORD.getName(), "cedar_messaging_password");

    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PORT.getName(), "3306");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_DB.getName(), "cedar_log");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_USER.getName(), "cedar_log_user");
    env.put(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PASSWORD.getName(), "cedar_log_password");

    env.put(CedarEnvironmentVariable.CEDAR_SALT_API_KEY.getName(), "salt");

    env.put(CedarEnvironmentVariable.CEDAR_VALIDATION_ENABLED.getName(), "false");

    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_1.getName(), "http://template-id-1");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_2.getName(), "http://template-id-2");

    env.put(CedarEnvironmentVariable.CEDAR_TRUSTED_FOLDERS.getName(),
        "{\\\"caDSR\\\":[\\\"https://repo.metadatacenter.orgx/folders/c3a7b03c-87bb-49c4-b311-2eb1bd398c4e\\\"]}");

    env.put(CedarEnvironmentVariable.CEDAR_BIOPORTAL_REST_BASE.getName(), "http://data.bioontology.org/");

    env.put(CedarEnvironmentVariable.CEDAR_OPENSEARCH_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_OPENSEARCH_TRANSPORT_PORT.getName(), "9300");

    env.put(CedarEnvironmentVariable.CEDAR_REDIS_PERSISTENT_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_REDIS_PERSISTENT_PORT.getName(), "6379");
    env.put(CedarEnvironmentVariable.CEDAR_REDIS_NONPERSISTENT_HOST.getName(), "127.0.0.1");
    env.put(CedarEnvironmentVariable.CEDAR_REDIS_NONPERSISTENT_PORT.getName(), "6380");

    env.put(CedarEnvironmentVariable.CEDAR_GROUP_HTTP_PORT.getName(), "9009");
    env.put(CedarEnvironmentVariable.CEDAR_GROUP_ADMIN_PORT.getName(), "9109");
    env.put(CedarEnvironmentVariable.CEDAR_GROUP_STOP_PORT.getName(), "9209");

    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_HTTP_PORT.getName(), "9012");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_ADMIN_PORT.getName(), "9112");
    env.put(CedarEnvironmentVariable.CEDAR_MESSAGING_STOP_PORT.getName(), "9212");

    env.put(CedarEnvironmentVariable.CEDAR_REPO_HTTP_PORT.getName(), "9002");
    env.put(CedarEnvironmentVariable.CEDAR_REPO_ADMIN_PORT.getName(), "9102");
    env.put(CedarEnvironmentVariable.CEDAR_REPO_STOP_PORT.getName(), "9202");

    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_HTTP_PORT.getName(), "9007");
    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_ADMIN_PORT.getName(), "9107");
    env.put(CedarEnvironmentVariable.CEDAR_RESOURCE_STOP_PORT.getName(), "9207");

    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_HTTP_PORT.getName(), "9003");
    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_ADMIN_PORT.getName(), "9103");
    env.put(CedarEnvironmentVariable.CEDAR_SCHEMA_STOP_PORT.getName(), "9203");

    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_HTTP_PORT.getName(), "9010");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_ADMIN_PORT.getName(), "9110");
    env.put(CedarEnvironmentVariable.CEDAR_SUBMISSION_STOP_PORT.getName(), "9210");

    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_HTTP_PORT.getName(), "9001");
    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_ADMIN_PORT.getName(), "9101");
    env.put(CedarEnvironmentVariable.CEDAR_ARTIFACT_STOP_PORT.getName(), "9201");

    env.put(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_HTTP_PORT.getName(), "9004");
    env.put(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_ADMIN_PORT.getName(), "9104");
    env.put(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_STOP_PORT.getName(), "9204");

    env.put(CedarEnvironmentVariable.CEDAR_USER_HTTP_PORT.getName(), "9005");
    env.put(CedarEnvironmentVariable.CEDAR_USER_ADMIN_PORT.getName(), "9105");
    env.put(CedarEnvironmentVariable.CEDAR_USER_STOP_PORT.getName(), "9205");

    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_HTTP_PORT.getName(), "9006");
    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_ADMIN_PORT.getName(), "9106");
    env.put(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_STOP_PORT.getName(), "9206");

    env.put(CedarEnvironmentVariable.CEDAR_WORKER_HTTP_PORT.getName(), "9011");
    env.put(CedarEnvironmentVariable.CEDAR_WORKER_ADMIN_PORT.getName(), "9111");
    env.put(CedarEnvironmentVariable.CEDAR_WORKER_STOP_PORT.getName(), "9211");

    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_HTTP_PORT.getName(), "9013");
    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_ADMIN_PORT.getName(), "9113");
    env.put(CedarEnvironmentVariable.CEDAR_OPENVIEW_STOP_PORT.getName(), "9213");

    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_HTTP_PORT.getName(), "9014");
    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_ADMIN_PORT.getName(), "9114");
    env.put(CedarEnvironmentVariable.CEDAR_MONITOR_STOP_PORT.getName(), "9214");

    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_HTTP_PORT.getName(), "9008");
    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_ADMIN_PORT.getName(), "9108");
    env.put(CedarEnvironmentVariable.CEDAR_IMPEX_STOP_PORT.getName(), "9208");

    env.put(CedarEnvironmentVariable.CEDAR_TEST_USER1_ID.getName(), "https://metadatacenter.org/users/user1-uuid");
    env.put(CedarEnvironmentVariable.CEDAR_TEST_USER2_ID.getName(), "https://metadatacenter.org/users/user2-uuid");

    TestUtil.setEnv(env);
  }

  private CedarConfig getCedarConfig() {
    return CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ALL));
  }

  @Test
  public void testGetInstance() throws Exception {
    CedarConfig instance = getCedarConfig();
    Assert.assertNotNull(instance);
  }

  @Test
  public void testKeycloakConfig() throws Exception {
    CedarConfig instance = getCedarConfig();
    KeycloakConfig keycloakConfig = instance.getKeycloakConfig();
    Assert.assertNotNull(keycloakConfig);
    Assert.assertEquals("admin-cli", keycloakConfig.getClientId());

    AdminUserConfig adminUser = instance.getAdminUserConfig();
    Assert.assertNotNull(adminUser);
    Assert.assertEquals("cedar-admin", adminUser.getUserName());
    Assert.assertEquals(CEDAR_ADMIN_USER_PASSWORD, adminUser.getPassword());
    Assert.assertEquals(CEDAR_ADMIN_USER_API_KEY, adminUser.getApiKey());
  }

  @Test
  public void testMongoConfig() throws Exception {
    CedarConfig instance = getCedarConfig();
    MongoConfig artifactServerConfig = instance.getArtifactServerConfig();
    Assert.assertNotNull(artifactServerConfig);
    Assert.assertEquals("cedar", artifactServerConfig.getDatabaseName());

    Map<String, String> artifactServerCollections = artifactServerConfig.getCollections();
    Assert.assertNotNull(artifactServerCollections);
    Assert.assertEquals("template-fields", artifactServerCollections.get("field"));
    Assert.assertEquals("template-elements", artifactServerCollections.get("element"));
    Assert.assertEquals("templates", artifactServerCollections.get("template"));
    Assert.assertEquals("template-instances", artifactServerCollections.get("instance"));

    MongoConfig userServerConfig = instance.getUserServerConfig();
    Assert.assertNotNull(userServerConfig);
    Assert.assertEquals("cedar", userServerConfig.getDatabaseName());

    Map<String, String> userServerCollections = userServerConfig.getCollections();
    Assert.assertNotNull(userServerCollections);

    Assert.assertEquals("users", userServerCollections.get("user"));
  }

  @Test
  public void testElasticSearchSearchConfig() throws Exception {
    CedarConfig instance = getCedarConfig();
    OpensearchSettingsMappingsConfig searchSettingsMappingsConfig = instance
        .getSearchSettingsMappingsConfig();
    Assert.assertNotNull(searchSettingsMappingsConfig);
    Assert.assertNotNull(searchSettingsMappingsConfig.getSettings());
    Assert.assertNotNull(searchSettingsMappingsConfig.getMappings());
    Assert.assertNotNull(searchSettingsMappingsConfig.getMappings().getDoc());

    Map<String, Object> settings = searchSettingsMappingsConfig.getSettings();
    Map<String, Object> index = (Map<String, Object>) settings.get("index");
    Map<String, Object> analysis = (Map<String, Object>) index.get("analysis");
    Map<String, Object> tokenizer = (Map<String, Object>) analysis.get("tokenizer");
    Map<String, Object> ngram_tokenizer = (Map<String, Object>) tokenizer.get("cedar_ngram_tokenizer");

    Assert.assertEquals("ngram", ngram_tokenizer.get("type"));
  }

  @Test
  public void testElasticSearchRulesConfig() throws Exception {
    CedarConfig instance = getCedarConfig();
    OpensearchSettingsMappingsConfig rulesSettingsMappingsConfig = instance
        .getSearchSettingsMappingsConfig();
    Assert.assertNotNull(rulesSettingsMappingsConfig);
    Assert.assertNotNull(rulesSettingsMappingsConfig.getSettings());
    Assert.assertNotNull(rulesSettingsMappingsConfig.getMappings());
    Assert.assertNotNull(rulesSettingsMappingsConfig.getMappings().getDoc());
  }

}
