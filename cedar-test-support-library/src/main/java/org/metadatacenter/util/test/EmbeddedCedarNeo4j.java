package org.metadatacenter.util.test;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.neo4j.GraphSchema;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.AppLoggerQueueService;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-process Neo4j for integration tests, replacing the live graph database. The embedded
 * server boots on a random port, so it can never collide with, or write into, a real Neo4j; it
 * runs without authentication, so the configured credentials are accepted as-is.
 *
 * The usual shape: call startAndRedirectEnvironment from a static initializer, before the
 * DropwizardAppRule starts the application (which reads the Neo4j environment variables when it
 * builds its configuration), then call seed after the application has started. The overload
 * taking extra environment entries lets a test also remap the server's own ports, so the test
 * instance never collides with a running development server.
 *
 * Servers that need the graph during startup itself (the worker resolves the admin user and
 * opens graph sessions in initializeApp) call startRedirectAndSeed instead: it initializes the
 * data services and seeds before any application code runs, on the configuration instance the
 * application will then reuse.
 *
 * Seeding creates the graph the way provisioning does: the uniqueness constraints the admin tool
 * creates ({@link GraphSchema#UNIQUE_CONSTRAINTS}), the global objects (root folders, Everybody
 * group, root category) through the admin session, then the TestAuthUtil users with Everybody
 * membership and home folders.
 *
 * The harness is closed when the JVM exits. It deletes its store directory only on close, so
 * without the close every test JVM left its whole store in the temporary directory.
 */
public final class EmbeddedCedarNeo4j {

  private static volatile Neo4j embedded;
  private static boolean seeded;
  private static boolean closesAtExit;

  private EmbeddedCedarNeo4j() {
  }

  public static void startAndRedirectEnvironment() {
    startAndRedirectEnvironment(Map.of());
  }

  public static synchronized void startAndRedirectEnvironment(Map<String, String> extraEnvironment) {
    if (embedded == null) {
      // Closing the harness shuts Bolt's Netty event-loop group down gracefully: every loop closes
      // its connections, then all of them wait out a five-second quiet period together, so a close
      // took about six seconds. The connections are already closed when the period starts, so a
      // zero period changes nothing a client can observe, and the close takes under one second.
      embedded = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
          .withConfig(GraphDatabaseInternalSettings.netty_server_shutdown_quiet_period, 0).build();
      closeAtExit();
    }
    // Re-applied even when the server is already up: in a shared JVM a later test class may
    // have replaced the override, and its own extra entries must land as well
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_NEO4J_HOST", embedded.boltURI().getHost());
    environment.put("CEDAR_NEO4J_BOLT_PORT", String.valueOf(embedded.boltURI().getPort()));
    environment.putAll(extraEnvironment);
    CedarEnvironmentSource.setOverride(environment);
  }

  /**
   * Stop the current harness so an already-booted application observes a real graph outage. The
   * next {@link #startAndRedirectEnvironment(Map)} call creates a fresh harness, and its first
   * {@link #seed(CedarConfig)} call rebuilds the global test fixtures. The application's existing
   * drivers are deliberately left open and pointed at the now-dead port; closing them would test a
   * client-lifecycle error rather than an unavailable Neo4j server.
   */
  public static synchronized void stopAndReset() {
    if (embedded != null) {
      embedded.close();
      embedded = null;
    }
    seeded = false;
  }

  /**
   * Empties the running graph in place and seeds it again, for a class that needs a fresh graph. It
   * deletes every node and relationship, drops every constraint and index, clears the seeded flag and
   * then calls {@link #seed(CedarConfig)}. The server, its Bolt port and every driver connected to it
   * stay as they are, so the reset costs a fraction of the second or more that
   * {@link #stopAndReset()} and a new harness take. The version lifecycle lock and the constraints the
   * admin tool restores after rebuilding a schema are created again. Other constraints an application
   * created when it started are dropped with the rest, as they are when the harness is restarted.
   *
   * @throws IllegalStateException when the harness is not running
   */
  public static synchronized void resetAndSeed(CedarConfig cedarConfig) throws Exception {
    if (embedded == null) {
      throw new IllegalStateException("The embedded Neo4j is not running, so there is nothing to reset");
    }
    GraphDatabaseService database = embedded.defaultDatabaseService();
    database.executeTransactionally("MATCH (n) DETACH DELETE n");
    // A constraint owns its backing index, so the constraints go first. The token lookup indexes
    // belong to every Neo4j database and are left in place.
    for (String constraint : names(database, "SHOW CONSTRAINTS YIELD name RETURN name")) {
      database.executeTransactionally("DROP CONSTRAINT `" + constraint + "` IF EXISTS");
    }
    for (String index : names(database, "SHOW INDEXES YIELD name, type WHERE type <> 'LOOKUP' RETURN name")) {
      database.executeTransactionally("DROP INDEX `" + index + "` IF EXISTS");
    }
    seeded = false;
    seed(cedarConfig);
    // The running proxies created the version lifecycle lock and its constraints once and remember
    // having done so, so the reset recreates them as the admin tool does after rebuilding the schema.
    CedarDataServices.getInstance().getAdminServiceSession(
        CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig))).ensureServerSchema();
  }

  private static List<String> names(GraphDatabaseService database, String query) {
    return database.executeTransactionally(query, Map.of(),
        result -> result.stream().map(row -> (String) row.get("name")).toList());
  }

  /**
   * Registered once, and deliberately unsynchronized: a shutdown hook that waited for this class's
   * lock could hang the JVM's exit behind a test thread that holds it.
   */
  private static void closeAtExit() {
    if (closesAtExit) {
      return;
    }
    closesAtExit = true;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      Neo4j current = embedded;
      if (current != null) {
        current.close();
      }
    }, "embedded-neo4j-close"));
  }

  public static synchronized void startRedirectAndSeed(SystemComponent systemComponent) {
    startAndRedirectEnvironment();

    // The application will reuse this singleton instance, built after the redirection
    Map<String, String> sandbox = CedarEnvironmentVariableProvider.getFor(systemComponent);
    CedarConfig cedarConfig = CedarConfig.getInstance(sandbox);
    CedarDataServices.initializeNeo4jServices(cedarConfig);
    CedarRequestContextFactory.init(cedarConfig.getLinkedDataUtil());
    // The Neo4j proxies log every query through AppLogger, which the application only
    // initializes at startup; seeding runs before that
    AppLogger.initLoggerQueueService(new AppLoggerQueueService(cedarConfig.getCacheConfig().getPersistent()),
        systemComponent);

    try {
      seed(cedarConfig);
    } catch (Exception e) {
      throw new IllegalStateException("Could not seed the embedded Neo4j", e);
    }
  }

  /**
   * Seeds at most once per JVM. Repeating the graph seeding would duplicate the users and the
   * CONTAINS chains they hang from, which corrupts path computation, so a second call in a
   * shared JVM is a no-op; the guard lives here so every caller is safe without discipline. A seed
   * that fails part-way empties the graph and leaves the guard open, so the next call starts again.
   */
  public static synchronized void seed(CedarConfig cedarConfig) throws Exception {
    if (seeded) {
      return;
    }
    try {
      CedarUser admin = TestAuthUtil.getAdminUser(cedarConfig);
      CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(admin);
      createProductionConstraints(adminContext);
      CedarDataServices.getInstance().getNeoUserService().createUser(admin);
      CedarDataServices.getInstance().getAdminServiceSession(adminContext).ensureGlobalObjectsExists();

      seedUser(TestAuthUtil.getTestUser1(cedarConfig));
      seedUser(TestAuthUtil.getTestUser2(cedarConfig));
    } catch (Exception | Error e) {
      // A partial seed must not count as one: every later class would skip seeding and fail on a missing
      // user or home folder, far from this failure. The graph is emptied so the next call seeds afresh.
      discardPartialSeed(e);
      throw e;
    }
    seeded = true;
    // Seeding gave the fixture users new home folders. The stored user service records a home folder
    // when it is created, so the in-memory one, which keeps copies, takes the fixtures again.
    TestAuthUtil.resetInMemoryUserService();
  }

  private static void discardPartialSeed(Throwable failure) {
    if (embedded == null) {
      return;
    }
    try {
      embedded.defaultDatabaseService().executeTransactionally("MATCH (n) DETACH DELETE n");
    } catch (RuntimeException e) {
      failure.addSuppressed(e);
    }
  }

  /**
   * Creates the uniqueness constraints a provisioned graph carries, so that a suite meets the rules the
   * deployed graph enforces: a second resource with an identifier already taken, a second group whose
   * name differs only in case, or a second folder or category of the same name under one parent is
   * refused here as it is in production.
   */
  private static void createProductionConstraints(CedarRequestContext adminContext) {
    AdminServiceSession admin = CedarDataServices.getInstance().getAdminServiceSession(adminContext);
    for (GraphSchema.UniqueConstraint constraint : GraphSchema.UNIQUE_CONSTRAINTS) {
      if (!admin.createUniqueConstraint(constraint.label(), constraint.properties())) {
        throw new IllegalStateException("Could not create the uniqueness constraint on "
            + constraint.label().getSimpleLabel() + " " + constraint.properties());
      }
    }
  }

  private static void seedUser(CedarUser user) throws Exception {
    CedarDataServices.getInstance().getNeoUserService().createUser(user);
    CedarRequestContext context = CedarRequestContextFactory.fromUser(user);
    CedarDataServices.getInstance().getUserServiceSession(context).addUserToEverybodyGroup(user.getResourceId());
    CedarDataServices.getInstance().getFolderServiceSession(context).ensureUserHomeExists();
  }

}
