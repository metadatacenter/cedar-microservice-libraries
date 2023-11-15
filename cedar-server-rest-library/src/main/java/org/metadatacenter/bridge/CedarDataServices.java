package org.metadatacenter.bridge;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConnection;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.*;
import org.metadatacenter.server.neo4j.proxy.*;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.neo4j.UserServiceNeo4j;
import org.metadatacenter.util.mongo.MongoClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CedarDataServices {

  private static final Logger log = LoggerFactory.getLogger(CedarDataServices.class);

  private UserService neoUserService;
  private Neo4JProxies proxies;
  private CedarConfig cedarConfig;
  private MongoClientFactory mongoClientFactoryForDocuments;
  private static final CedarDataServices instance = new CedarDataServices();

  private CedarDataServices() {
  }

  public static void initializeMongoClientFactoryForDocuments(MongoConnection mongoConnection) {
    instance.mongoClientFactoryForDocuments = new MongoClientFactory(mongoConnection);
    instance.mongoClientFactoryForDocuments.buildClient();
  }

  public static void initializeNeo4jServices(CedarConfig cedarConfig) {
    instance.cedarConfig = cedarConfig;
    instance.proxies = new Neo4JProxies(cedarConfig);
    instance.neoUserService = new UserServiceNeo4j(instance.proxies.user());
  }

  public static GroupServiceSession getGroupServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionGroupService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static CategoryServiceSession getCategoryServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionCategoryService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static GraphServiceSession getGraphServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionGraphService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static ResourcePermissionServiceSession getResourcePermissionServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionResourcePermissionService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static CategoryPermissionServiceSession getCategoryPermissionServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionCategoryPermissionService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static VersionServiceSession getVersionServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionVersionService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static AdminServiceSession getAdminServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionAdminService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  public static FolderServiceSession getFolderServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionFolderService.get(instance.cedarConfig, instance.proxies, context.getCedarUser(),
          context.getGlobalRequestIdHeader(),
          context.getLocalRequestIdHeader());
    }
  }

  public static InclusionSubgraphServiceSession getInclusionSubgraphServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionInclusionSubgraphService.get(instance.cedarConfig, instance.proxies, context.getCedarUser(),
          context.getGlobalRequestIdHeader(),
          context.getLocalRequestIdHeader());
    }
  }

  public static UserServiceSession getUserServiceSession(CedarRequestContext context) {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return Neo4JUserSessionUserService
          .get(instance.cedarConfig, instance.proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
              context.getLocalRequestIdHeader());
    }
  }

  // DO NOT USE unless you need internal functionality
  public static Neo4JProxies getProxies() {
    if (instance.proxies == null) {
      log.error("You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
      System.exit(-2);
      return null;
    } else {
      return instance.proxies;
    }
  }

  public static UserService getNeoUserService() {
    if (instance.neoUserService == null) {
      log.error("You need to initialize neo user service: CedarDataServices.initializeNeoUserService()");
      System.exit(-1);
      return null;
    } else {
      return instance.neoUserService;
    }
  }

  public static MongoClientFactory getMongoClientFactoryForDocuments() {
    if (instance.mongoClientFactoryForDocuments == null) {
      log.error("You need to initialize mongoClientFactory: " +
          "CedarDataServices.initializeMongoClientFactoryForDocuments(mongoConnection)");
      System.exit(-1);
      return null;
    } else {
      return instance.mongoClientFactoryForDocuments;
    }
  }

}
