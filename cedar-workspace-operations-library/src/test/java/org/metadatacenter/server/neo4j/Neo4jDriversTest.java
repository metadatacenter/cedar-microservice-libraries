package org.metadatacenter.server.neo4j;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Neo4jDriversTest {

  private static final String PROPERTY = "cedar.test.dependencyTimeoutMillis";

  private String previous;

  @BeforeEach
  void rememberTheProperty() {
    previous = System.getProperty(PROPERTY);
  }

  @AfterEach
  void restoreTheProperty() {
    if (previous == null) {
      System.clearProperty(PROPERTY);
    } else {
      System.setProperty(PROPERTY, previous);
    }
  }

  @Test
  void healthyConnectionEstablishmentHasHeadroomBeyondTheFastOutageTimeout() {
    assertEquals(5_000, Neo4jDrivers.testConnectionTimeoutMillis(1_000));
  }

  @Test
  void aLargerConfiguredTimeoutIsPreserved() {
    assertEquals(10_000, Neo4jDrivers.testConnectionTimeoutMillis(10_000));
  }

  @Test
  void aTestRuntimeDriverKeepsTheConnectionFloorAndTheShortWaits() {
    System.setProperty(PROPERTY, "1000");

    Config settings = Neo4jDrivers.settings(coordinates(null));

    assertEquals(5_000, settings.connectionTimeoutMillis());
    assertEquals(1_000, settings.connectionAcquisitionTimeoutMillis());
  }

  @Test
  void withoutTheTestPropertyTheDriverKeepsItsOwnDefaults() {
    System.clearProperty(PROPERTY);

    Config settings = Neo4jDrivers.settings(coordinates(null));
    Config defaults = Config.defaultConfig();

    assertEquals(defaults.connectionTimeoutMillis(), settings.connectionTimeoutMillis());
    assertEquals(defaults.connectionAcquisitionTimeoutMillis(), settings.connectionAcquisitionTimeoutMillis());
    assertEquals(defaults.maxConnectionPoolSize(), settings.maxConnectionPoolSize());
  }

  @Test
  void aConfiguredPoolCeilingIsApplied() {
    assertEquals(7, Neo4jDrivers.settings(coordinates(7)).maxConnectionPoolSize());
  }

  private static Neo4jConfig coordinates(Integer maxConnectionPoolSize) {
    Neo4jConfig neo4j = mock(Neo4jConfig.class);
    when(neo4j.getMaxConnectionPoolSize()).thenReturn(maxConnectionPoolSize);
    return neo4j;
  }
}
