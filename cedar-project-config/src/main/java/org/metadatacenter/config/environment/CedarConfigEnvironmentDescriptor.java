package org.metadatacenter.config.environment;

import org.metadatacenter.model.SystemComponent;

import java.util.*;

public class CedarConfigEnvironmentDescriptor {

  private static final Map<CedarEnvironmentVariable, Set<SystemComponent>> variableToComponent;
  private static final Map<SystemComponent, Set<CedarEnvironmentVariable>> componentToVariable;

  static {
    List<SystemComponent> allMicroservices = new ArrayList<>();
    for (SystemComponent sc : SystemComponent.values()) {
      if (sc.getServerName() != null) {
        allMicroservices.add(sc);
      }
    }

    variableToComponent = new LinkedHashMap<>();
    for (CedarEnvironmentVariable env : CedarEnvironmentVariable.values()) {
      Set<SystemComponent> systemComponents = new LinkedHashSet<>();
      systemComponents.add(SystemComponent.ALL);
      variableToComponent.put(env, systemComponents);
    }

    Set<SystemComponent> cedarVersion = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VERSION);
    // gulpfile.js replaces in version.js
    cedarVersion.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarVersion.add(SystemComponent.FRONTEND_TEST);
    cedarVersion.add(SystemComponent.FRONTEND_PRODUCTION);

    Set<SystemComponent> cedarVersionModifier = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VERSION_MODIFIER);
    // gulpfile.js replaces in version.js
    cedarVersionModifier.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarVersionModifier.add(SystemComponent.FRONTEND_TEST);
    cedarVersionModifier.add(SystemComponent.FRONTEND_PRODUCTION);

    Set<SystemComponent> cedarHome = variableToComponent.get(CedarEnvironmentVariable.CEDAR_HOME);
    cedarHome.add(SystemComponent.ADMIN_TOOL); // generate-nginx-config, export dir
    cedarHome.add(SystemComponent.CADSR_TOOL);
    cedarHome.add(SystemComponent.UTIL_BIN); // utility shell scrips

    Set<SystemComponent> keycloakHome = variableToComponent.get(CedarEnvironmentVariable.KEYCLOAK_HOME);
    keycloakHome.add(SystemComponent.UTIL_BIN); // utility shell scrips

    Set<SystemComponent> nginxHome = variableToComponent.get(CedarEnvironmentVariable.NGINX_HOME);
    nginxHome.add(SystemComponent.UTIL_BIN); // utility shell scrips

    Set<SystemComponent> cedarFrontendBeh = variableToComponent.get(CedarEnvironmentVariable.CEDAR_FRONTEND_BEHAVIOR);
    // gulpfile.js decides that it should or not start a server for the front end code
    cedarFrontendBeh.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarFrontendBeh.add(SystemComponent.FRONTEND_TEST);
    cedarFrontendBeh.add(SystemComponent.FRONTEND_PRODUCTION);

    Set<SystemComponent> cedarFrontendTarget = variableToComponent.get(CedarEnvironmentVariable.CEDAR_FRONTEND_TARGET);
    // gulpfile.js replaces this in several files, frontend connects to this server
    cedarFrontendTarget.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarFrontendTarget.add(SystemComponent.FRONTEND_TEST);
    cedarFrontendTarget.add(SystemComponent.FRONTEND_PRODUCTION);

    Set<SystemComponent> cedarHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_HOST);
    cedarHost.add(SystemComponent.ADMIN_TOOL); // generate-nginx-config, all Keycloak related tasks
    cedarHost.add(SystemComponent.CADSR_TOOL);
    // gulpfile.js replaces values in url-service.conf.json and test-env.js
    cedarHost.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarHost.add(SystemComponent.FRONTEND_TEST);
    cedarHost.add(SystemComponent.FRONTEND_PRODUCTION);
    // all the microservices
    cedarHost.addAll(allMicroservices);

    Set<SystemComponent> cedarNetGateway = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NET_GATEWAY);
    cedarNetGateway.add(SystemComponent.ADMIN_TOOL);
    cedarNetGateway.add(SystemComponent.CADSR_TOOL);
    // all the microservices
    cedarNetGateway.addAll(allMicroservices);

    Set<SystemComponent> nciCADSRValueSetsOntologyFilePath = variableToComponent.get(CedarEnvironmentVariable.CEDAR_CADSR_ONTOLOGIES_FOLDER);
    nciCADSRValueSetsOntologyFilePath.add(SystemComponent.SERVER_RESOURCE);

    Set<SystemComponent> cedarBioportalApiKey = variableToComponent.get(CedarEnvironmentVariable.CEDAR_BIOPORTAL_API_KEY);
    cedarBioportalApiKey.add(SystemComponent.SERVER_TERMINOLOGY);

    Set<SystemComponent> cedarBioportalRestBase = variableToComponent.get(CedarEnvironmentVariable.CEDAR_BIOPORTAL_REST_BASE);
    cedarBioportalRestBase.add(SystemComponent.SERVER_TERMINOLOGY);

    Set<SystemComponent> cedarAnalyticsKey = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ANALYTICS_KEY);
    // gulpfile.js replaces int tracking-service.conf.json
    cedarAnalyticsKey.add(SystemComponent.FRONTEND_DEVELOPMENT);
    cedarAnalyticsKey.add(SystemComponent.FRONTEND_TEST);
    cedarAnalyticsKey.add(SystemComponent.FRONTEND_PRODUCTION);

    Set<SystemComponent> cedarNcbiSraFtpPassword = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_PASSWORD);
    cedarNcbiSraFtpPassword.add(SystemComponent.SERVER_SUBMISSION);

    Set<SystemComponent> cedarNcbiSraFtpUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_USER);
    cedarNcbiSraFtpUser.addAll(cedarNcbiSraFtpPassword);

    Set<SystemComponent> cedarNcbiSraFtpHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_HOST);
    cedarNcbiSraFtpHost.addAll(cedarNcbiSraFtpPassword);

    Set<SystemComponent> cedarNcbiSraFtpDirectory = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NCBI_SRA_FTP_DIRECTORY);
    cedarNcbiSraFtpDirectory.addAll(cedarNcbiSraFtpPassword);

    Set<SystemComponent> cedarImmPortSubmissionUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_USER);
    cedarImmPortSubmissionUser.add(SystemComponent.SERVER_SUBMISSION);

    Set<SystemComponent> cedarImmPortSubmissionPassword = variableToComponent.get(CedarEnvironmentVariable.CEDAR_IMMPORT_SUBMISSION_PASSWORD);
    cedarImmPortSubmissionPassword.add(SystemComponent.SERVER_SUBMISSION);

    Set<SystemComponent> cedarNeo4jUserPassword = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NEO4J_USER_PASSWORD);
    cedarNeo4jUserPassword.add(SystemComponent.ADMIN_TOOL);
    cedarNeo4jUserPassword.add(SystemComponent.CADSR_TOOL);
    cedarNeo4jUserPassword.addAll(allMicroservices);

    Set<SystemComponent> cedarNeo4jUserName = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NEO4J_USER_NAME);
    cedarNeo4jUserName.addAll(cedarNeo4jUserPassword);

    Set<SystemComponent> cedarNeo4jHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NEO4J_HOST);
    cedarNeo4jHost.addAll(cedarNeo4jUserPassword);

    Set<SystemComponent> cedarNeo4jBoltPort = variableToComponent.get(CedarEnvironmentVariable.CEDAR_NEO4J_BOLT_PORT);
    cedarNeo4jBoltPort.addAll(cedarNeo4jUserPassword);

    Set<SystemComponent> cedarAdminUserApiKey = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ADMIN_USER_API_KEY);
    cedarAdminUserApiKey.add(SystemComponent.ADMIN_TOOL); // all neo4j, keycloak related tasks + search reindex
    cedarAdminUserApiKey.add(SystemComponent.CADSR_TOOL);
    cedarAdminUserApiKey.add(SystemComponent.KEYCLOAK_EVENT_LISTENER); // user login callback, auth with this
    cedarAdminUserApiKey.add(SystemComponent.SERVER_RESOURCE); // index regeneration
    cedarAdminUserApiKey.add(SystemComponent.SERVER_WORKER); // SearchPermissionExecutorService
    cedarAdminUserApiKey.add(SystemComponent.SERVER_MESSAGING); // messages from processes
    cedarAdminUserApiKey.add(SystemComponent.SERVER_SUBMISSION);
    cedarAdminUserApiKey.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarAdminUserPasswd = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ADMIN_USER_PASSWORD);
    cedarAdminUserPasswd.add(SystemComponent.ADMIN_TOOL); // all keycloak related tasks
    cedarAdminUserPasswd.add(SystemComponent.CADSR_TOOL);
    cedarAdminUserPasswd.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarCaDSRAdminUserApiKey = variableToComponent.get(CedarEnvironmentVariable.CEDAR_CADSR_ADMIN_USER_API_KEY);
    cedarCaDSRAdminUserApiKey.add(SystemComponent.ADMIN_TOOL);
    cedarCaDSRAdminUserApiKey.add(SystemComponent.CADSR_TOOL);
    cedarCaDSRAdminUserApiKey.add(SystemComponent.SERVER_IMPEX);

    Set<SystemComponent> cedarMongoUserName = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_NAME);
    cedarMongoUserName.add(SystemComponent.ADMIN_TOOL);
    cedarMongoUserName.add(SystemComponent.CADSR_TOOL);
    // all the microservices for MongoDB initialization for user details
    cedarMongoUserName.addAll(allMicroservices);

    Set<SystemComponent> cedarMongoUserPasswd = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MONGO_APP_USER_PASSWORD);
    cedarMongoUserPasswd.addAll(cedarMongoUserName);// used together with the username

    Set<SystemComponent> cedarMongoHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MONGO_HOST);
    cedarMongoHost.addAll(cedarMongoUserName);// used together with the username

    Set<SystemComponent> cedarPortMongo = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MONGO_PORT);
    cedarPortMongo.addAll(cedarMongoUserName);// used together with the username

    Set<SystemComponent> cedarElasticsearchHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ELASTICSEARCH_HOST);
    cedarElasticsearchHost.add(SystemComponent.SERVER_WORKER);
    cedarElasticsearchHost.add(SystemComponent.SERVER_VALUERECOMMENDER);
    cedarElasticsearchHost.add(SystemComponent.SERVER_RESOURCE);
    cedarElasticsearchHost.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarElasticsearchTransportPort = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ELASTICSEARCH_TRANSPORT_PORT);
    cedarElasticsearchTransportPort.addAll(cedarElasticsearchHost);

    Set<SystemComponent> cedarMessagingMysqlHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_HOST);
    cedarMessagingMysqlHost.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarMessagingMysqlPort = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PORT);
    cedarMessagingMysqlPort.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarMessagingMysqlDb = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_DB);
    cedarMessagingMysqlDb.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarMessagingMysqlUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_USER);
    cedarMessagingMysqlUser.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarMessagingMysqlPassword = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_MYSQL_PASSWORD);
    cedarMessagingMysqlPassword.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarLoggingMysqlHost = variableToComponent.get(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_HOST);
    cedarLoggingMysqlHost.add(SystemComponent.SERVER_WORKER);
    cedarLoggingMysqlHost.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarLoggingMysqlPort = variableToComponent.get(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PORT);
    cedarLoggingMysqlPort.add(SystemComponent.SERVER_WORKER);
    cedarLoggingMysqlPort.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarLoggingMysqlDb = variableToComponent.get(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_DB);
    cedarLoggingMysqlDb.add(SystemComponent.SERVER_WORKER);
    cedarLoggingMysqlDb.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarLoggingMysqlUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_USER);
    cedarLoggingMysqlUser.add(SystemComponent.SERVER_WORKER);
    cedarLoggingMysqlUser.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarLoggingMysqlPassword = variableToComponent.get(CedarEnvironmentVariable.CEDAR_LOG_MYSQL_PASSWORD);
    cedarLoggingMysqlPassword.add(SystemComponent.SERVER_WORKER);
    cedarLoggingMysqlPassword.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarValidationEnabled = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VALIDATION_ENABLED);
    cedarValidationEnabled.add(SystemComponent.SERVER_ARTIFACT);

    Set<SystemComponent> submissionTemplateId1 = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_1);
    submissionTemplateId1.add(SystemComponent.SERVER_WORKER);
    submissionTemplateId1.add(SystemComponent.SERVER_RESOURCE);

    Set<SystemComponent> submissionTemplateId2 = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SUBMISSION_TEMPLATE_ID_2);
    submissionTemplateId2.add(SystemComponent.SERVER_WORKER);
    submissionTemplateId2.add(SystemComponent.SERVER_RESOURCE);

    Set<SystemComponent> cedarSaltApiKey = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SALT_API_KEY);
    cedarSaltApiKey.add(SystemComponent.ADMIN_TOOL); //profile creation
    cedarSaltApiKey.add(SystemComponent.CADSR_TOOL);
    cedarSaltApiKey.add(SystemComponent.SERVER_RESOURCE); //profile creation triggered by event listener

    Set<SystemComponent> redisPersistentHost = variableToComponent.get(CedarEnvironmentVariable
        .CEDAR_REDIS_PERSISTENT_HOST);
    redisPersistentHost.addAll(allMicroservices);
    redisPersistentHost.add(SystemComponent.ADMIN_TOOL);
    redisPersistentHost.add(SystemComponent.CADSR_TOOL);

    Set<SystemComponent> redisPersistentPort = variableToComponent.get(CedarEnvironmentVariable.CEDAR_REDIS_PERSISTENT_PORT);
    redisPersistentPort.addAll(allMicroservices);
    redisPersistentPort.add(SystemComponent.ADMIN_TOOL);
    redisPersistentPort.add(SystemComponent.CADSR_TOOL);

    Set<SystemComponent> cedarHttpPortGroup = variableToComponent.get(CedarEnvironmentVariable.CEDAR_GROUP_HTTP_PORT);
    cedarHttpPortGroup.add(SystemComponent.SERVER_GROUP);
    Set<SystemComponent> cedarAdminPortGroup = variableToComponent.get(CedarEnvironmentVariable.CEDAR_GROUP_ADMIN_PORT);
    cedarAdminPortGroup.add(SystemComponent.SERVER_GROUP);
    Set<SystemComponent> cedarStopPortGroup = variableToComponent.get(CedarEnvironmentVariable.CEDAR_GROUP_STOP_PORT);
    cedarStopPortGroup.add(SystemComponent.SERVER_GROUP);

    Set<SystemComponent> cedarHttpPortMessaging = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_HTTP_PORT);
    cedarHttpPortMessaging.add(SystemComponent.SERVER_MESSAGING);
    cedarHttpPortMessaging.add(SystemComponent.SERVER_SUBMISSION);
    Set<SystemComponent> cedarAdminPortMessaging = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_ADMIN_PORT);
    cedarAdminPortMessaging.add(SystemComponent.SERVER_MESSAGING);
    Set<SystemComponent> cedarStopPortMessaging = variableToComponent.get(CedarEnvironmentVariable.CEDAR_MESSAGING_STOP_PORT);
    cedarStopPortMessaging.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarHttpPortUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_USER_HTTP_PORT);
    cedarHttpPortUser.addAll(allMicroservices);
    cedarHttpPortUser.remove(SystemComponent.SERVER_OPENVIEW);
    Set<SystemComponent> cedarAdminPortUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_USER_ADMIN_PORT);
    cedarAdminPortUser.add(SystemComponent.SERVER_USER);
    Set<SystemComponent> cedarStopPortUser = variableToComponent.get(CedarEnvironmentVariable.CEDAR_USER_STOP_PORT);
    cedarStopPortUser.add(SystemComponent.SERVER_USER);

    Set<SystemComponent> cedarHttpPortRepo = variableToComponent.get(CedarEnvironmentVariable.CEDAR_REPO_HTTP_PORT);
    cedarHttpPortRepo.add(SystemComponent.SERVER_REPO);
    Set<SystemComponent> cedarAdminPortRepo = variableToComponent.get(CedarEnvironmentVariable.CEDAR_REPO_ADMIN_PORT);
    cedarAdminPortRepo.add(SystemComponent.SERVER_REPO);
    Set<SystemComponent> cedarStopPortRepo = variableToComponent.get(CedarEnvironmentVariable.CEDAR_REPO_STOP_PORT);
    cedarStopPortRepo.add(SystemComponent.SERVER_REPO);

    Set<SystemComponent> cedarHttpPortResource = variableToComponent.get(CedarEnvironmentVariable.CEDAR_RESOURCE_HTTP_PORT);
    cedarHttpPortResource.add(SystemComponent.SERVER_RESOURCE);
    cedarHttpPortResource.add(SystemComponent.ADMIN_TOOL);
    cedarHttpPortResource.add(SystemComponent.CADSR_TOOL);
    Set<SystemComponent> cedarAdminPortResource = variableToComponent.get(CedarEnvironmentVariable.CEDAR_RESOURCE_ADMIN_PORT);
    cedarAdminPortResource.add(SystemComponent.SERVER_RESOURCE);
    Set<SystemComponent> cedarStopPortResource = variableToComponent.get(CedarEnvironmentVariable.CEDAR_RESOURCE_STOP_PORT);
    cedarStopPortResource.add(SystemComponent.SERVER_RESOURCE);

    Set<SystemComponent> cedarHttpPortSchema = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SCHEMA_HTTP_PORT);
    cedarHttpPortSchema.add(SystemComponent.SERVER_SCHEMA);
    Set<SystemComponent> cedarAdminPortSchema = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SCHEMA_ADMIN_PORT);
    cedarAdminPortSchema.add(SystemComponent.SERVER_SCHEMA);
    Set<SystemComponent> cedarStopPortSchema = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SCHEMA_STOP_PORT);
    cedarStopPortSchema.add(SystemComponent.SERVER_SCHEMA);

    Set<SystemComponent> cedarPortArtifact = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ARTIFACT_HTTP_PORT);
    cedarPortArtifact.add(SystemComponent.SERVER_ARTIFACT);
    cedarPortArtifact.add(SystemComponent.SERVER_RESOURCE);
    cedarPortArtifact.add(SystemComponent.SERVER_WORKER);
    Set<SystemComponent> cedarAdminPortTemplate = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ARTIFACT_ADMIN_PORT);
    cedarAdminPortTemplate.add(SystemComponent.SERVER_ARTIFACT);
    Set<SystemComponent> cedarStopPortTemplate = variableToComponent.get(CedarEnvironmentVariable.CEDAR_ARTIFACT_STOP_PORT);
    cedarStopPortTemplate.add(SystemComponent.SERVER_ARTIFACT);

    Set<SystemComponent> cedarHttpPortTerminology = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_HTTP_PORT);
    cedarHttpPortTerminology.add(SystemComponent.SERVER_TERMINOLOGY);
    Set<SystemComponent> cedarAdminPortTerminology = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_ADMIN_PORT);
    cedarAdminPortTerminology.add(SystemComponent.SERVER_TERMINOLOGY);
    Set<SystemComponent> cedarStopPortTerminology = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TERMINOLOGY_STOP_PORT);
    cedarStopPortTerminology.add(SystemComponent.SERVER_TERMINOLOGY);

    Set<SystemComponent> cedarHttpPortValuerecommender = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_HTTP_PORT);
    cedarHttpPortValuerecommender.add(SystemComponent.SERVER_VALUERECOMMENDER);
    cedarHttpPortValuerecommender.add(SystemComponent.SERVER_WORKER);
    Set<SystemComponent> cedarAdminPortValuerecommender = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_ADMIN_PORT);
    cedarAdminPortValuerecommender.add(SystemComponent.SERVER_VALUERECOMMENDER);
    Set<SystemComponent> cedarStopPortValuerecommender = variableToComponent.get(CedarEnvironmentVariable.CEDAR_VALUERECOMMENDER_STOP_PORT);
    cedarStopPortValuerecommender.add(SystemComponent.SERVER_VALUERECOMMENDER);

    Set<SystemComponent> cedarHttpPortSubmission = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SUBMISSION_HTTP_PORT);
    cedarHttpPortSubmission.add(SystemComponent.SERVER_SUBMISSION);
    Set<SystemComponent> cedarAdminPortSubmission = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SUBMISSION_ADMIN_PORT);
    cedarAdminPortSubmission.add(SystemComponent.SERVER_SUBMISSION);
    Set<SystemComponent> cedarStopPortSubmission = variableToComponent.get(CedarEnvironmentVariable.CEDAR_SUBMISSION_STOP_PORT);
    cedarStopPortSubmission.add(SystemComponent.SERVER_SUBMISSION);

    Set<SystemComponent> cedarHttpPortWorker = variableToComponent.get(CedarEnvironmentVariable.CEDAR_WORKER_HTTP_PORT);
    cedarHttpPortWorker.add(SystemComponent.SERVER_WORKER);
    Set<SystemComponent> cedarAdminPortWorker = variableToComponent.get(CedarEnvironmentVariable.CEDAR_WORKER_ADMIN_PORT);
    cedarAdminPortWorker.add(SystemComponent.SERVER_WORKER);
    Set<SystemComponent> cedarStopPortWorker = variableToComponent.get(CedarEnvironmentVariable.CEDAR_WORKER_STOP_PORT);
    cedarStopPortWorker.add(SystemComponent.SERVER_WORKER);

    Set<SystemComponent> cedarTestUser1Id = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TEST_USER1_ID);
    cedarTestUser1Id.add(SystemComponent.SERVER_ARTIFACT);
    cedarTestUser1Id.add(SystemComponent.SERVER_TERMINOLOGY);
    cedarTestUser1Id.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarTestUser2Id = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TEST_USER2_ID);
    cedarTestUser2Id.add(SystemComponent.SERVER_MESSAGING);

    Set<SystemComponent> cedarTrustedFolders = variableToComponent.get(CedarEnvironmentVariable.CEDAR_TRUSTED_FOLDERS);
    cedarTrustedFolders.add(SystemComponent.SERVER_WORKER);
    cedarTrustedFolders.add(SystemComponent.SERVER_RESOURCE);

    Set<SystemComponent> cedarHttpPortOpenview = variableToComponent.get(CedarEnvironmentVariable.CEDAR_OPENVIEW_HTTP_PORT);
    cedarHttpPortOpenview.add(SystemComponent.SERVER_OPENVIEW);
    Set<SystemComponent> cedarAdminPortOpenview = variableToComponent.get(CedarEnvironmentVariable.CEDAR_OPENVIEW_ADMIN_PORT);
    cedarAdminPortOpenview.add(SystemComponent.SERVER_OPENVIEW);
    Set<SystemComponent> cedarStopPortOpenview = variableToComponent.get(CedarEnvironmentVariable.CEDAR_OPENVIEW_STOP_PORT);
    cedarStopPortOpenview.add(SystemComponent.SERVER_OPENVIEW);

    Set<SystemComponent> cedarHttpPortInternals = variableToComponent.get(CedarEnvironmentVariable.CEDAR_INTERNALS_HTTP_PORT);
    cedarHttpPortInternals.add(SystemComponent.SERVER_INTERNALS);
    Set<SystemComponent> cedarAdminPortInternals = variableToComponent.get(CedarEnvironmentVariable.CEDAR_INTERNALS_ADMIN_PORT);
    cedarAdminPortInternals.add(SystemComponent.SERVER_INTERNALS);
    Set<SystemComponent> cedarStopPortInternals = variableToComponent.get(CedarEnvironmentVariable.CEDAR_INTERNALS_STOP_PORT);
    cedarStopPortInternals.add(SystemComponent.SERVER_INTERNALS);

    Set<SystemComponent> cedarHttpPortImpex = variableToComponent.get(CedarEnvironmentVariable.CEDAR_IMPEX_HTTP_PORT);
    cedarHttpPortImpex.add(SystemComponent.SERVER_IMPEX);
    Set<SystemComponent> cedarAdminPortImpex = variableToComponent.get(CedarEnvironmentVariable.CEDAR_IMPEX_ADMIN_PORT);
    cedarAdminPortImpex.add(SystemComponent.SERVER_IMPEX);
    Set<SystemComponent> cedarStopPortImpex = variableToComponent.get(CedarEnvironmentVariable.CEDAR_IMPEX_STOP_PORT);
    cedarStopPortImpex.add(SystemComponent.SERVER_IMPEX);

    // Compute the reverse map
    componentToVariable = new LinkedHashMap<>();
    for (SystemComponent component : SystemComponent.values()) {
      Set<CedarEnvironmentVariable> variables = new LinkedHashSet<>();
      componentToVariable.put(component, variables);
      for (CedarEnvironmentVariable variable : CedarEnvironmentVariable.values()) {
        Set<SystemComponent> componentsForVariable = variableToComponent.get(variable);
        if (componentsForVariable.contains(component)) {
          variables.add(variable);
        }
      }
    }
  }

  public static Set<CedarEnvironmentVariable> getVariableNamesFor(SystemComponent useCase) {
    return componentToVariable.get(useCase);
  }
}
