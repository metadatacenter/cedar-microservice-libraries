package org.metadatacenter.util.test;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherSessionListener;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Holds the embedded Mongo child process to the lifecycle its test JVM owns, and to what it provisions. */
@Tag("integration")
public class EmbeddedCedarMongoTest {

  @Test
  public void closeProcessClosesTheResourceExactlyOnce() {
    AtomicInteger closeCount = new AtomicInteger();

    EmbeddedCedarMongo.closeProcess(closeCount::incrementAndGet);

    Assertions.assertEquals(1, closeCount.get());
  }

  @Test
  public void embeddedProcessTeardownIsRegisteredForTheLauncherSession() {
    boolean registered = ServiceLoader.load(LauncherSessionListener.class).stream()
        .map(ServiceLoader.Provider::type)
        .anyMatch(CedarEmbeddedProcessSessionListener.class::equals);

    Assertions.assertTrue(registered);
  }

  /**
   * The harness provisions what the deployed store has: an application user the CEDAR client logs in
   * as, and a unique identifier index on each artifact collection. Stopping it frees the process.
   */
  @Test
  public void theStartedStoreIsProvisionedAndStops() {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    String host = CedarEnvironmentSource.get("CEDAR_MONGO_HOST");
    int port = Integer.parseInt(CedarEnvironmentSource.get("CEDAR_MONGO_PORT"));
    String user = CedarEnvironmentSource.get("CEDAR_MONGO_APP_USER_NAME");
    String password = CedarEnvironmentSource.get("CEDAR_MONGO_APP_USER_PASSWORD");
    try {
      try (MongoClient client = MongoClients.create(MongoClientSettings.builder()
          .applyToClusterSettings(cluster -> cluster.hosts(List.of(new ServerAddress(host, port))))
          .credential(MongoCredential.createCredential(user, "cedar", password.toCharArray()))
          .build())) {
        for (String collection : EmbeddedCedarMongo.ARTIFACT_COLLECTIONS) {
          MongoCollection<Document> documents = client.getDatabase("cedar").getCollection(collection);
          String id = "https://repo.metadatacenter.orgx/" + collection + "/" + UUID.randomUUID();
          documents.insertOne(new Document("@id", id));
          MongoWriteException duplicate = Assertions.assertThrows(MongoWriteException.class,
              () -> documents.insertOne(new Document("@id", id)), collection + " accepted a repeated identifier");
          Assertions.assertEquals(ErrorCategory.DUPLICATE_KEY, duplicate.getError().getCategory());
        }
      }
    } finally {
      EmbeddedCedarMongo.stop();
    }

    try (Socket ignored = new Socket(host, port)) {
      Assertions.fail("the stopped mongod still accepts connections on " + port);
    } catch (IOException expected) {
      // Refused: the process is gone.
    }
  }
}
