package org.metadatacenter.util.mongo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClientSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.config.MongoConnection;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoClientFactoryTest {

  private String previousTestTimeout;

  @BeforeEach
  void clearConfiguredTestTimeout() {
    previousTestTimeout = System.getProperty(CedarTestRuntime.DEPENDENCY_TIMEOUT_MILLIS_PROPERTY);
    System.clearProperty(CedarTestRuntime.DEPENDENCY_TIMEOUT_MILLIS_PROPERTY);
  }

  @AfterEach
  void restoreTestTimeout() {
    if (previousTestTimeout == null) {
      System.clearProperty(CedarTestRuntime.DEPENDENCY_TIMEOUT_MILLIS_PROPERTY);
    } else {
      System.setProperty(CedarTestRuntime.DEPENDENCY_TIMEOUT_MILLIS_PROPERTY, previousTestTimeout);
    }
  }

  @Test
  void productionSettingsBoundServerSocketAndPoolWaits() throws Exception {
    MongoClientSettings settings = new MongoClientFactory(connection()).buildSettings();

    assertEquals(30000, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
    assertEquals(10000, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
    assertEquals(60000, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
    assertEquals(120000, settings.getConnectionPoolSettings().getMaxWaitTime(TimeUnit.MILLISECONDS));
    assertEquals(100, settings.getConnectionPoolSettings().getMaxSize());
  }

  @Test
  void testRuntimeCanTightenEveryWaitWithoutChangingPoolCapacity() throws Exception {
    System.setProperty(CedarTestRuntime.DEPENDENCY_TIMEOUT_MILLIS_PROPERTY, "250");

    MongoClientSettings settings = new MongoClientFactory(connection()).buildSettings();

    assertEquals(250, settings.getClusterSettings().getServerSelectionTimeout(TimeUnit.MILLISECONDS));
    assertEquals(250, settings.getSocketSettings().getConnectTimeout(TimeUnit.MILLISECONDS));
    assertEquals(250, settings.getSocketSettings().getReadTimeout(TimeUnit.MILLISECONDS));
    assertEquals(250, settings.getConnectionPoolSettings().getMaxWaitTime(TimeUnit.MILLISECONDS));
    assertEquals(100, settings.getConnectionPoolSettings().getMaxSize());
  }

  private MongoConnection connection() throws Exception {
    return new ObjectMapper().readValue("""
        {
          "host": "127.0.0.1",
          "port": 27017,
          "user": "cedar",
          "password": "secret",
          "databaseName": "cedar",
          "serverSelectionTimeoutMillis": 30000,
          "connectTimeoutMillis": 10000,
          "readTimeoutMillis": 60000,
          "poolWaitTimeoutMillis": 120000,
          "maxPoolSize": 100
        }
        """, MongoConnection.class);
  }
}
