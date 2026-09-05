package org.metadatacenter.util.test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import org.bson.Document;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-process MongoDB for integration tests, replacing the live document store. Call
 * startAndRedirectEnvironment from a static initializer, before anything builds the CEDAR
 * configuration: it boots a real mongod on a random port (so it can never collide with, or
 * write into, a real MongoDB), provisions the application user - the CEDAR Mongo client always
 * authenticates, so the account must exist - and redirects the CEDAR Mongo environment
 * variables. Collections are created lazily by the driver; no schema setup is involved.
 *
 * The mongod version tracks the deployed MongoDB major line. The binary is downloaded on first
 * use and cached under ~/.embedmongo.
 */
public final class EmbeddedCedarMongo {

  private static final String DATABASE_NAME = "cedar";
  private static final String DEFAULT_TEST_USER = "cedar-test";
  private static final String DEFAULT_TEST_PASSWORD = "cedar-test-password";

  private static TransitionWalker.ReachedState<RunningMongodProcess> running;
  private static String userName;
  private static String password;
  private static boolean shutdownHookRegistered;

  private EmbeddedCedarMongo() {
  }

  public static void startAndRedirectEnvironment() {
    startAndRedirectEnvironment(Map.of());
  }

  public static synchronized void startAndRedirectEnvironment(Map<String, String> extraEnvironment) {
    if (running == null) {
      running = Mongod.instance().start(Version.Main.V5_0);
      registerShutdownHook();
      ServerAddress address = running.current().getServerAddress();

      userName = valueOrDefault(CedarEnvironmentSource.get("CEDAR_MONGO_APP_USER_NAME"), DEFAULT_TEST_USER);
      password = valueOrDefault(CedarEnvironmentSource.get("CEDAR_MONGO_APP_USER_PASSWORD"), DEFAULT_TEST_PASSWORD);
      try (MongoClient client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
        client.getDatabase(DATABASE_NAME).runCommand(new Document("createUser", userName)
            .append("pwd", password)
            .append("roles", List.of(new Document("role", "readWrite").append("db", DATABASE_NAME))));
      }
    }
    // Re-applied even when the server is already up: in a shared JVM a later test class may
    // have replaced the override, and its own extra entries must land as well
    ServerAddress address = running.current().getServerAddress();
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_MONGO_HOST", address.getHost());
    environment.put("CEDAR_MONGO_PORT", String.valueOf(address.getPort()));
    environment.put("CEDAR_MONGO_APP_USER_NAME", userName);
    environment.put("CEDAR_MONGO_APP_USER_PASSWORD", password);
    environment.putAll(extraEnvironment);
    CedarEnvironmentSource.setOverride(environment);
  }

  /**
   * Stops the child process synchronously. Package-private so the lifecycle regression test can
   * prove that closing the reached state releases the listening port; normal consumers rely on the
   * JVM shutdown hook registered when the process starts.
   */
  static synchronized void stop() {
    if (running == null) {
      return;
    }
    try {
      closeProcess(running);
    } finally {
      running = null;
      userName = null;
      password = null;
    }
  }

  static void closeProcess(AutoCloseable process) {
    try {
      process.close();
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Could not stop the embedded Mongo process", e);
    }
  }

  private static void registerShutdownHook() {
    if (!shutdownHookRegistered) {
      Runtime.getRuntime().addShutdownHook(
          new Thread(EmbeddedCedarMongo::stop, "embedded-cedar-mongo-shutdown"));
      shutdownHookRegistered = true;
    }
  }

  private static String valueOrDefault(String value, String defaultValue) {
    return value == null || value.isBlank() ? defaultValue : value;
  }

}
