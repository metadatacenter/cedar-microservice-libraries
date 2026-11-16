package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.Map;

/**
 * A seed that fails part-way leaves no partial graph behind and does not count as a seed, so the next
 * class seeds afresh instead of running against a graph without its users or home folders.
 */
@Tag("integration")
class EmbeddedCedarNeo4jSeedFailureTest {

  private static final Map<String, String> REDIS_UNREACHABLE = Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1");

  @Test
  void aFailedSeedLeavesAnEmptyGraphAndTheNextSeedSucceeds() throws Exception {
    EmbeddedCedarNeo4j.stopAndReset();
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(REDIS_UNREACHABLE);
    String boltUri = "bolt://" + CedarEnvironmentSource.get("CEDAR_NEO4J_HOST") + ":"
        + CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");

    try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.none()); Session session = driver.session()) {
      // Two groups whose names differ only in case break the constraint the seed creates first.
      String nameLower = Neo4JUtil.escapePropertyName(NodeProperty.NAME_LOWER.getValue());
      session.run("CREATE (:Group {`" + nameLower + "`: 'duplicate'}), (:Group {`" + nameLower + "`: 'duplicate'})")
          .consume();

      Assertions.assertThrows(IllegalStateException.class,
          () -> EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE));

      Assertions.assertEquals(0, session.run("MATCH (n) RETURN count(n) AS c").single().get("c").asLong(),
          "a failed seed must not leave part of the graph behind");

      EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    }

    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    Assertions.assertNotNull(CedarDataServices.getInstance()
        .getFolderServiceSession(CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig)))
        .findHomeFolderOf(), "the seed after the failure creates the test users and their home folders");
  }

  /** Leaves the next class a seeded graph on a running server, whatever happened above. */
  @AfterAll
  static void leaveASeededGraph() {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(REDIS_UNREACHABLE);
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
  }
}
