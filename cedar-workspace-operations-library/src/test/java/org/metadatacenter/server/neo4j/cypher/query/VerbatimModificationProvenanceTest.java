package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.*;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.ArtifactModificationProvenance;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderArtifact;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.*;
import org.neo4j.harness.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the same update query as the graph repository, including null removal and numeric sort keys. */
class VerbatimModificationProvenanceTest {
  static Neo4j neo;
  static Driver database;
  @BeforeAll static void start() {
    neo = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    database = GraphDatabase.driver(neo.boltURI(), AuthTokens.none());
  }
  @AfterAll static void stop() { database.close(); neo.close(); }
  @BeforeEach void seed() {
    try (var session = database.session()) {
      session.run("MATCH (n) DETACH DELETE n").consume();
      session.run("CREATE (:Artifact {_id:'artifact', `pav_createdOn`:'2000-01-01T00:00:00Z', "
          + "`pav_createdBy`:'creator', `oslc_modifiedBy`:'repair-admin', "
          + "`pav_lastUpdatedOn`:'2026-09-25T20:02:23-07:00', lastUpdatedOnTS:1790391743})").consume();
    }
  }
  org.neo4j.driver.types.Node update(ArtifactModificationProvenance provenance) {
    var fields = Map.of(NodeProperty.DESCRIPTION, "Repaired declaration");
    var query = new CypherQueryWithParameters(CypherQueryBuilderArtifact.updateResourceById(fields),
        CypherParamBuilderArtifact.updateArtifactById(CedarTemplateId.build("artifact"), fields,
            CedarUserId.build("caller"), provenance));
    try (var session = database.session()) {
      return session.run(query.getRunnableQuery(), query.getParameterMap()).single().get("artifact").asNode();
    }
  }
  @Test void historicalAuthorAndOffsetTimestampSurviveARepairAndSortNumerically() {
    String date = "2017-12-29T08:48:17.987-08:00";
    var result = update(new ArtifactModificationProvenance("original-author", date));
    assertEquals("original-author", result.get("oslc_modifiedBy").asString());
    assertEquals(OffsetDateTime.parse(date).toEpochSecond(),
        OffsetDateTime.parse(result.get("pav_lastUpdatedOn").asString()).toEpochSecond());
    assertEquals(OffsetDateTime.parse(date).toEpochSecond(), result.get("lastUpdatedOnTS").asLong());
    assertEquals("creator", result.get("pav_createdBy").asString());
    assertEquals("2000-01-01T00:00:00Z", result.get("pav_createdOn").asString());
  }
  @Test void unknownModificationIsNotInventedFromTheRepairAccount() {
    var result = update(new ArtifactModificationProvenance(null, null));
    assertFalse(result.containsKey("oslc_modifiedBy"));
    assertFalse(result.containsKey("pav_lastUpdatedOn"));
    assertFalse(result.containsKey("lastUpdatedOnTS"));
    assertEquals("creator", result.get("pav_createdBy").asString());
  }
  @Test void ordinaryUpdatesStillStampTheCallerAndCurrentTime() {
    long before = Instant.now().getEpochSecond();
    var result = update(null);
    assertEquals("caller", result.get("oslc_modifiedBy").asString());
    assertTrue(result.get("lastUpdatedOnTS").asLong() >= before);
  }
  @Test void documentParserDoesNotCoerceMalformedProvenance() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> ArtifactModificationProvenance.fromDocument(
        JsonMapper.STRICT_MAPPER.readTree("{\"oslc:modifiedBy\":23}")));
    assertThrows(java.time.format.DateTimeParseException.class, () -> ArtifactModificationProvenance.fromDocument(
        JsonMapper.STRICT_MAPPER.readTree("{\"pav:lastUpdatedOn\":\"2026-09-25\"}")));
    assertEquals(new ArtifactModificationProvenance(null, null),
        ArtifactModificationProvenance.fromDocument(JsonMapper.STRICT_MAPPER.readTree("{}")));
  }
}
