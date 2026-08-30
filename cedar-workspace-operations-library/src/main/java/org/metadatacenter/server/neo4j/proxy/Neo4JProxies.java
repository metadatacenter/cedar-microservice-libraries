package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.PathUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class Neo4JProxies {

  private static final Logger log = LoggerFactory.getLogger(Neo4JProxies.class);

  // The shared outage override deliberately makes pool acquisition and retries fail quickly. A
  // healthy embedded Bolt server can still need more than one second for its first handshake on a
  // constrained CI runner, so connection establishment needs independent headroom.
  static final long MIN_TEST_CONNECTION_TIMEOUT_MILLIS = 5_000;

  protected final CedarConfig cedarConfig;
  protected final Neo4jConfig config;
  protected final PathUtil pathUtil;
  protected final LinkedDataUtil linkedDataUtil;

  /**
   * The one driver every proxy in this set uses. The Neo4j driver is thread-safe and holds the
   * connection pool, so a set needs exactly one; a service that built twelve spread its load across
   * twelve pools that could not share a warm connection between them.
   */
  final Driver driver;

  private final Neo4JProxyAdmin adminProxy;
  private final Neo4JProxyFolder folderProxy;
  private final Neo4JProxyGroup groupProxy;
  private final Neo4JProxyUser userProxy;
  private final Neo4JProxyResourcePermission permissionProxy;
  private final Neo4JProxyArtifact artifactProxy;
  private final Neo4JProxyResource resourceProxy;
  private final Neo4JProxyFilesystemResource filesystemResourceProxy;
  private final Neo4JProxyGraph graphProxy;
  private final Neo4JProxyVersion versionProxy;
  private final Neo4JProxyCategory categoryProxy;
  private final Neo4JProxyCategoryPermission categoryPermissionProxy;

  public Neo4JProxies(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    this.config = Neo4jConfig.fromCedarConfig(cedarConfig);
    this.driver = buildDriver(this.config);
    this.linkedDataUtil = cedarConfig.getLinkedDataUtil();
    this.pathUtil = new Neo4JPathUtil(cedarConfig);

    this.adminProxy = new Neo4JProxyAdmin(this, cedarConfig);
    this.folderProxy = new Neo4JProxyFolder(this, cedarConfig);
    this.groupProxy = new Neo4JProxyGroup(this, cedarConfig);
    this.userProxy = new Neo4JProxyUser(this, cedarConfig);
    this.permissionProxy = new Neo4JProxyResourcePermission(this, cedarConfig);
    this.artifactProxy = new Neo4JProxyArtifact(this, cedarConfig);
    this.resourceProxy = new Neo4JProxyResource(this, cedarConfig);
    this.filesystemResourceProxy = new Neo4JProxyFilesystemResource(this, cedarConfig);
    this.graphProxy = new Neo4JProxyGraph(this, cedarConfig);
    this.versionProxy = new Neo4JProxyVersion(this, cedarConfig);
    this.categoryProxy = new Neo4JProxyCategory(this, cedarConfig);
    this.categoryPermissionProxy = new Neo4JProxyCategoryPermission(this, cedarConfig);
  }

  private static Driver buildDriver(Neo4jConfig config) {
    Config.ConfigBuilder driverConfig = Config.builder();
    CedarTestRuntime.dependencyTimeoutMillis().ifPresent(timeout -> driverConfig
        .withConnectionTimeout(testConnectionTimeoutMillis(timeout), TimeUnit.MILLISECONDS)
        .withConnectionAcquisitionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withMaxTransactionRetryTime(timeout, TimeUnit.MILLISECONDS));
    return GraphDatabase.driver(config.getUri(),
        AuthTokens.basic(config.getUserName(), config.getUserPassword()),
        driverConfig.build());
  }

  static long testConnectionTimeoutMillis(long dependencyTimeoutMillis) {
    return Math.max(dependencyTimeoutMillis, MIN_TEST_CONNECTION_TIMEOUT_MILLIS);
  }

  public void verifyConnectivity() {
    driver.verifyConnectivity();
  }

  /**
   * Closes the set's Neo4j driver, releasing its connection pool and Netty event-loop threads.
   * Nothing reclaims them on garbage collection. In production this never runs — the application
   * boots once — but a shared test JVM re-boots the application per class, and without closing the
   * previous set the drivers pile up until the process cannot create another event loop and a later
   * test class fails to start.
   */
  public void close() {
    try {
      driver.close();
    } catch (RuntimeException e) {
      log.warn("Error closing the Neo4j driver", e);
    }
  }

  public Neo4JProxyAdmin admin() {
    return adminProxy;
  }

  public Neo4JProxyFolder folder() {
    return folderProxy;
  }

  public Neo4JProxyGroup group() {
    return groupProxy;
  }

  public Neo4JProxyUser user() {
    return userProxy;
  }

  public Neo4JProxyResourcePermission permission() {
    return permissionProxy;
  }

  public Neo4JProxyArtifact artifact() {
    return artifactProxy;
  }

  public Neo4JProxyResource resource() {
    return resourceProxy;
  }

  public Neo4JProxyFilesystemResource filesystemResource() {
    return filesystemResourceProxy;
  }

  public Neo4JProxyGraph graph() {
    return graphProxy;
  }

  public Neo4JProxyVersion version() {
    return versionProxy;
  }

  public Neo4JProxyCategory category() {
    return categoryProxy;
  }

  public Neo4JProxyCategoryPermission categoryPermission() {
    return categoryPermissionProxy;
  }

  public LinkedDataUtil getLinkedDataUtil() {
    return linkedDataUtil;
  }
}
