package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/** Holds the embedded Mongo child process to the lifecycle its test JVM owns. */
public class EmbeddedCedarMongoTest {

  private Map<String, String> previousEnvironment;

  @BeforeEach
  public void rememberEnvironment() {
    previousEnvironment = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;
  }

  @AfterEach
  public void stopMongoAndRestoreEnvironment() {
    EmbeddedCedarMongo.stop();
    CedarEnvironmentSource.setOverride(previousEnvironment);
  }

  @Test
  public void stopReleasesTheChildProcessPort() throws Exception {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    String host = CedarEnvironmentSource.get("CEDAR_MONGO_HOST");
    int port = Integer.parseInt(CedarEnvironmentSource.get("CEDAR_MONGO_PORT"));

    Assertions.assertDoesNotThrow(() -> connect(host, port),
        "the embedded Mongo process should own its configured port while running");

    EmbeddedCedarMongo.stop();

    Assertions.assertThrows(IOException.class, () -> connect(host, port),
        "closing the reached state must stop mongod and release its port");
  }

  private static void connect(String host, int port) throws IOException {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 1_000);
    }
  }
}
