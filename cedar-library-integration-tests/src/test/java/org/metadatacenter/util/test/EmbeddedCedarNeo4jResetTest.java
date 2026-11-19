package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.Map;

/**
 * The in-place reset gives a class an empty, freshly seeded graph on the server that is already running.
 */
@Tag("integration")
class EmbeddedCedarNeo4jResetTest {

  @Test
  void aResetLeavesOnlyTheSeedOnTheSameServer() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    String boltPort = CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");
    // Earlier classes in the JVM leave their fixtures in the shared graph, so the seed alone is
    // measured on a graph that has just been reset.
    EmbeddedCedarNeo4j.resetAndSeed(cedarConfig);

    try (Driver driver = GraphDatabase.driver("bolt://127.0.0.1:" + boltPort, AuthTokens.none());
         Session session = driver.session()) {
      long seededNodes = count(session, "MATCH (n) RETURN count(n) AS c");
      long seededSchema = count(session, "SHOW CONSTRAINTS YIELD name RETURN count(name) AS c")
          + count(session, "SHOW INDEXES YIELD type WHERE type <> 'LOOKUP' RETURN count(*) AS c");
      session.run("CREATE (:ResetProbe {name: 'left behind'})-[:NEXT]->(:ResetProbe)").consume();
      session.run("CREATE CONSTRAINT reset_probe_name IF NOT EXISTS FOR (p:ResetProbe) REQUIRE p.name IS UNIQUE").consume();
      session.run("CREATE INDEX reset_probe_other IF NOT EXISTS FOR (p:ResetProbeOther) ON (p.name)").consume();

      EmbeddedCedarNeo4j.resetAndSeed(cedarConfig);

      Assertions.assertEquals(boltPort, CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT"),
          "the server keeps its port, so connected drivers keep working");
      Assertions.assertEquals(0, count(session, "MATCH (p:ResetProbe) RETURN count(p) AS c"));
      Assertions.assertEquals(0, count(session,
          "SHOW CONSTRAINTS YIELD name WHERE name = 'reset_probe_name' RETURN count(name) AS c"));
      Assertions.assertEquals(0, count(session,
          "SHOW INDEXES YIELD name WHERE name = 'reset_probe_other' RETURN count(name) AS c"));
      Assertions.assertEquals(seededNodes, count(session, "MATCH (n) RETURN count(n) AS c"),
          "the graph holds the seed again, once");
      Assertions.assertEquals(seededSchema, count(session, "SHOW CONSTRAINTS YIELD name RETURN count(name) AS c")
          + count(session, "SHOW INDEXES YIELD type WHERE type <> 'LOOKUP' RETURN count(*) AS c"),
          "the schema is what seeding creates, and nothing else");
    }

    Assertions.assertNotNull(CedarDataServices.getInstance()
        .getFolderServiceSession(CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig)))
        .findHomeFolderOf(), "the test users are seeded again with their home folders");
  }

  private static long count(Session session, String query) {
    return session.run(query).single().get("c").asLong();
  }
}
