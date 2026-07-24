package org.metadatacenter.util.test;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import org.bson.Document;

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

  private static TransitionWalker.ReachedState<RunningMongodProcess> running;

  private EmbeddedCedarMongo() {
  }

  public static void startAndRedirectEnvironment() {
    startAndRedirectEnvironment(Map.of());
  }

  public static synchronized void startAndRedirectEnvironment(Map<String, String> extraEnvironment) {
    if (running == null) {
      running = Mongod.instance().start(Version.Main.V5_0);
      ServerAddress address = running.current().getServerAddress();

      String userName = System.getenv("CEDAR_MONGO_APP_USER_NAME");
      String password = System.getenv("CEDAR_MONGO_APP_USER_PASSWORD");
      try (MongoClient client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort())) {
        client.getDatabase(DATABASE_NAME).runCommand(new Document("createUser", userName)
            .append("pwd", password)
            .append("roles", List.of(new Document("role", "readWrite").append("db", DATABASE_NAME))));
      }

      Map<String, String> environment = new HashMap<>(System.getenv());
      environment.put("CEDAR_MONGO_HOST", address.getHost());
      environment.put("CEDAR_MONGO_PORT", String.valueOf(address.getPort()));
      environment.putAll(extraEnvironment);
      TestUtil.setEnv(environment);
    }
  }

}
