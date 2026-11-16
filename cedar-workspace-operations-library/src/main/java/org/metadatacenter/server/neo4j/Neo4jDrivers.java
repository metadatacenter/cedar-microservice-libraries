package org.metadatacenter.server.neo4j;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarTestRuntime;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.util.concurrent.TimeUnit;

/**
 * Opens every Neo4j driver a CEDAR service builds from its configuration, so that each one takes the
 * same settings.
 *
 * <p>A test JVM sets {@code cedar.test.dependencyTimeoutMillis}, normally to one second, so that pool
 * acquisition and transaction retry against an unavailable graph fail quickly. A healthy embedded Bolt
 * server can still take more than a second for its first handshake on a loaded machine, and a driver
 * that gave connection establishment the same second failed test classes at start-up. Connection
 * establishment therefore keeps a floor of {@link #MIN_TEST_CONNECTION_TIMEOUT_MILLIS}. A dead local
 * port still refuses at once. Outside a test JVM the property is absent and the driver keeps its own
 * defaults.</p>
 */
public final class Neo4jDrivers {

  /** The least time a test-runtime driver allows for establishing a connection. */
  public static final long MIN_TEST_CONNECTION_TIMEOUT_MILLIS = 5_000;

  private Neo4jDrivers() {
  }

  /** Opens a driver for the graph the configuration identifies. */
  public static Driver open(CedarConfig cedarConfig) {
    return open(Neo4jConfig.fromCedarConfig(cedarConfig));
  }

  /** Opens a driver for the given graph coordinates. */
  public static Driver open(Neo4jConfig neo4j) {
    return GraphDatabase.driver(neo4j.getUri(),
        AuthTokens.basic(neo4j.getUserName(), neo4j.getUserPassword()), settings(neo4j));
  }

  /**
   * The driver settings {@link #open(Neo4jConfig)} uses: the configured pool ceiling, when one is set,
   * and the test runtime's waits, when the property is present.
   */
  public static Config settings(Neo4jConfig neo4j) {
    Config.ConfigBuilder settings = Config.builder();
    if (neo4j.getMaxConnectionPoolSize() != null) {
      settings.withMaxConnectionPoolSize(neo4j.getMaxConnectionPoolSize());
    }
    CedarTestRuntime.dependencyTimeoutMillis().ifPresent(timeout -> settings
        .withConnectionTimeout(testConnectionTimeoutMillis(timeout), TimeUnit.MILLISECONDS)
        .withConnectionAcquisitionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withMaxTransactionRetryTime(timeout, TimeUnit.MILLISECONDS));
    return settings.build();
  }

  /** The connection timeout a test-runtime driver takes for the given dependency timeout. */
  public static long testConnectionTimeoutMillis(long dependencyTimeoutMillis) {
    return Math.max(dependencyTimeoutMillis, MIN_TEST_CONNECTION_TIMEOUT_MILLIS);
  }
}
