package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.neo4j.GraphSchema;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The seeded embedded graph carries the uniqueness constraints a provisioned graph carries, so a
 * suite cannot store what production refuses.
 */
@Tag("integration")
class EmbeddedCedarNeo4jSchemaTest {

  private static String boltUri;

  @BeforeAll
  static void startSeededGraph() {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    boltUri = "bolt://" + CedarEnvironmentSource.get("CEDAR_NEO4J_HOST") + ":"
        + CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");
  }

  @Test
  void everyProductionUniquenessConstraintExists() {
    try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.none()); Session session = driver.session()) {
      Set<String> present = session.run("SHOW CONSTRAINTS YIELD type, labelsOrTypes, properties "
              + "WHERE type = 'UNIQUENESS' RETURN labelsOrTypes, properties").list().stream()
          .map(row -> describe(row.get("labelsOrTypes").asList(Value::asString),
              row.get("properties").asList(Value::asString)))
          .collect(Collectors.toSet());

      for (GraphSchema.UniqueConstraint constraint : GraphSchema.UNIQUE_CONSTRAINTS) {
        String expected = describe(List.of(constraint.label().getSimpleLabel()),
            constraint.properties().stream().map(p -> Neo4JUtil.escapePropertyName(p.getValue())).toList());
        Assertions.assertTrue(present.contains(expected), expected + " is missing from " + present);
      }
    }
  }

  @Test
  void aSecondGroupWhoseNameDiffersOnlyInCaseIsRefused() {
    try (Driver driver = GraphDatabase.driver(boltUri, AuthTokens.none()); Session session = driver.session()) {
      String property = Neo4JUtil.escapePropertyName(NodeProperty.NAME_LOWER.getValue());
      session.run("CREATE (:Group {`" + property + "`: 'schema test group'})").consume();
      Assertions.assertThrows(ClientException.class,
          () -> session.run("CREATE (:Group {`" + property + "`: 'schema test group'})").consume());
      session.run("MATCH (g:Group {`" + property + "`: 'schema test group'}) DELETE g").consume();
    }
  }

  private static String describe(List<String> labels, List<String> properties) {
    return labels + " " + properties;
  }
}
