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
  private MongoConnection mongoConnectionForDocuments;
  private static final CedarDataServices instance = new CedarDataServices();

  private CedarDataServices() {
  }

  // Re-initialization with the same connection is a no-op. In production each initializer runs
  // once, at startup; in a shared test JVM every application boot runs it again, and each
  // abandoned client or proxy set would leak its connection pool (the Neo4j drivers alone hold
  // enough file descriptors that a test suite exhausts the process limit). The same CedarConfig
  // yields the same connection object, so an identity check recognizes a repeated boot.
  public static void initializeMongoClientFactoryForDocuments(MongoConnection mongoConnection) {
    if (instance.mongoClientFactoryForDocuments != null
        && instance.mongoConnectionForDocuments == mongoConnection) {
      return;
    }
    instance.mongoConnectionForDocuments = mongoConnection;
    instance.mongoClientFactoryForDocuments = new MongoClientFactory(mongoConnection);
    instance.mongoClientFactoryForDocuments.buildClient();
  }

  public static void initializeNeo4jServices(CedarConfig cedarConfig) {
    if (instance.proxies != null && instance.cedarConfig == cedarConfig) {
      return;
    }
    // A different config means the previous proxy set is being replaced. Close its drivers first, or
    // each abandoned set leaks a dozen Neo4j connection pools and their Netty event-loop threads — in a
    // shared test JVM that re-boots the app per class, that exhausts the process. Never runs in
    // production, where the initializer runs once with a single config.
    if (instance.proxies != null) {
      instance.proxies.close();
    }
    instance.cedarConfig = cedarConfig;
    instance.proxies = new Neo4JProxies(cedarConfig);
    instance.neoUserService = new UserServiceNeo4j(instance.proxies.user());
  }

  // The single, managed services object. Its accessors are instance methods so that a consumer
  // receives this object and calls them explicitly, rather than reaching a global static from
  // anywhere. Only a composition root — a server's Application, the test harness — should call this,
  // once, and inject the result into the classes below it; those classes must never call it
  // themselves. The one singleton persists because the Neo4j proxies and Mongo client it holds are
  // genuinely process-level, and a shared test JVM depends on reusing them across app boots (see
  // initializeNeo4jServices). What retired was the scattered static accessor, not this holder.
  public static CedarDataServices getInstance() {
    return instance;
  }

  private void requireNeo4j() {
    if (proxies == null) {
      throw new IllegalStateException(
          "You need to initialize Neo4j services:CedarDataServices.initializeNeo4jServices(cedarConfig)");
    }
  }

  public GroupServiceSession getGroupServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionGroupService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public CategoryServiceSession getCategoryServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionCategoryService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public GraphServiceSession getGraphServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionGraphService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public ResourcePermissionServiceSession getResourcePermissionServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionResourcePermissionService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public CategoryPermissionServiceSession getCategoryPermissionServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionCategoryPermissionService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public VersionServiceSession getVersionServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionVersionService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public AdminServiceSession getAdminServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionAdminService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  public FolderServiceSession getFolderServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionFolderService.get(cedarConfig, proxies, context.getCedarUser(),
        context.getGlobalRequestIdHeader(),
        context.getLocalRequestIdHeader());
  }

  public InclusionSubgraphServiceSession getInclusionSubgraphServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionInclusionSubgraphService.get(cedarConfig, proxies, context.getCedarUser(),
        context.getGlobalRequestIdHeader(),
        context.getLocalRequestIdHeader());
  }

  public UserServiceSession getUserServiceSession(CedarRequestContext context) {
    requireNeo4j();
    return Neo4JUserSessionUserService
        .get(cedarConfig, proxies, context.getCedarUser(), context.getGlobalRequestIdHeader(),
            context.getLocalRequestIdHeader());
  }

  // DO NOT USE unless you need internal functionality
  public Neo4JProxies getProxies() {
    requireNeo4j();
    return proxies;
  }

  public UserService getNeoUserService() {
    if (neoUserService == null) {
      throw new IllegalStateException("You need to initialize neo user service: CedarDataServices.initializeNeoUserService()");
    }
    return neoUserService;
  }

  public MongoClientFactory getMongoClientFactoryForDocuments() {
    if (mongoClientFactoryForDocuments == null) {
      throw new IllegalStateException("You need to initialize mongoClientFactory: " +
          "CedarDataServices.initializeMongoClientFactoryForDocuments(mongoConnection)");
    }
    return mongoClientFactoryForDocuments;
  }

}
