package org.metadatacenter.util.test;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.AppLoggerQueueService;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.HashMap;
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
 * Seeding creates the graph skeleton the way provisioning does: the global objects (root
 * folders, Everybody group, root category) through the admin session, then the TestAuthUtil
 * users with Everybody membership and home folders.
 */
public final class EmbeddedCedarNeo4j {

  private static Neo4j embedded;
  private static boolean seeded;

  private EmbeddedCedarNeo4j() {
  }

  public static void startAndRedirectEnvironment() {
    startAndRedirectEnvironment(Map.of());
  }

  public static synchronized void startAndRedirectEnvironment(Map<String, String> extraEnvironment) {
    if (embedded == null) {
      embedded = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    }
    // Re-applied even when the server is already up: in a shared JVM a later test class may
    // have replaced the override, and its own extra entries must land as well
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_NEO4J_HOST", embedded.boltURI().getHost());
    environment.put("CEDAR_NEO4J_BOLT_PORT", String.valueOf(embedded.boltURI().getPort()));
    environment.putAll(extraEnvironment);
    CedarEnvironmentSource.setOverride(environment);
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
   * shared JVM is a no-op; the guard lives here so every caller is safe without discipline.
   */
  public static synchronized void seed(CedarConfig cedarConfig) throws Exception {
    if (seeded) {
      return;
    }
    seeded = true;
    CedarUser admin = TestAuthUtil.getAdminUser(cedarConfig);
    CedarDataServices.getInstance().getNeoUserService().createUser(admin);
    CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(admin);
    CedarDataServices.getInstance().getAdminServiceSession(adminContext).ensureGlobalObjectsExists();

    seedUser(TestAuthUtil.getTestUser1(cedarConfig));
    seedUser(TestAuthUtil.getTestUser2(cedarConfig));
  }

  private static void seedUser(CedarUser user) throws Exception {
    CedarDataServices.getInstance().getNeoUserService().createUser(user);
    CedarRequestContext context = CedarRequestContextFactory.fromUser(user);
    CedarDataServices.getInstance().getUserServiceSession(context).addUserToEverybodyGroup(user.getResourceId());
    CedarDataServices.getInstance().getFolderServiceSession(context).ensureUserHomeExists();
  }

}
