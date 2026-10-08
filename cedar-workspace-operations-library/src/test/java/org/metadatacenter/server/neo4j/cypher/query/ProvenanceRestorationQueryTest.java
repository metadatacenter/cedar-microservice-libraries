package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.*;
import org.neo4j.driver.*;
import org.neo4j.harness.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Proves the exact prepared operator query on Neo4j without touching an application artifact. */
class ProvenanceRestorationQueryTest {
  static Neo4j neo;
  static Driver database;
  static String query;
  @BeforeAll static void start() throws Exception {
    neo = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    database = GraphDatabase.driver(neo.boltURI(), AuthTokens.none());
    try (var resource = ProvenanceRestorationQueryTest.class.getResourceAsStream(
        "/org/metadatacenter/server/restore-artifact-provenance.cypher")) {
      assertNotNull(resource); query = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
  @AfterAll static void stop() { database.close(); neo.close(); }
  @BeforeEach void seed() {
    try (var session = database.session()) {
      session.run("MATCH (n) DETACH DELETE n").consume();
      session.run("CREATE (:CedarVersionLock {id:'lifecycle'}), "
          + "(:Artifact {_id:'a', oslc_modifiedBy:'admin', lastUpdatedOnTS:200, pav_lastUpdatedOn:'repair', "
          + "pav_createdBy:'creator', name:'Unchanged', ownedBy:'owner', pav_version:'1.0.0', bibo_status:'bibo:published'}), "
          + "(:Artifact {_id:'b', oslc_modifiedBy:'admin', lastUpdatedOnTS:200})").consume();
    }
  }
  static Map<String,Object> row(String id) {
    return Map.of("id", id, "expectedModifiedBy", "admin", "expectedLastUpdatedOnTS", 200L,
        "expectedGraphRevision", 1L, "modifiedBy", "original", "lastUpdatedOn", "1970-01-01T00:01:40Z",
        "lastUpdatedOnTS", 100L);
  }
  @Test void restoresOnlyModificationMetadataAndQueuesTheSameArtifactForProjection() {
    try (var session = database.session()) {
      assertEquals(1, session.run(query, Map.of("rows", List.of(row("a")))).list().size());
      var node = session.run("MATCH (a:Artifact {_id:'a'}) RETURN a").single().get("a").asNode();
      assertEquals("original", node.get("oslc_modifiedBy").asString());
      assertEquals(100, node.get("lastUpdatedOnTS").asLong());
      assertEquals("creator", node.get("pav_createdBy").asString());
      assertEquals("Unchanged", node.get("name").asString());
      assertEquals("owner", node.get("ownedBy").asString());
      assertEquals("1.0.0", node.get("pav_version").asString());
      assertEquals("bibo:published", node.get("bibo_status").asString());
      assertEquals(2, node.get("_cedarRevision").asLong());
      assertEquals("a", session.run("MATCH (p:CedarVersionProjection) RETURN p.resourceId AS id").single().get("id").asString());
      assertTrue(session.run(query, Map.of("rows", List.of(row("a")))).list().isEmpty(), "cannot apply twice");
    }
  }
  @Test void oneLaterEditPreventsEveryArtifactWriteInTheBatch() {
    try (var session = database.session()) {
      session.run("MATCH (a:Artifact {_id:'b'}) SET a.oslc_modifiedBy='later-author', a.lastUpdatedOnTS=300").consume();
      assertTrue(session.run(query, Map.of("rows", List.of(row("a"), row("b")))).list().isEmpty());
      assertEquals("admin", session.run("MATCH (a:Artifact {_id:'a'}) RETURN a.oslc_modifiedBy AS author").single().get("author").asString());
      assertEquals(0, session.run("MATCH (p:CedarVersionProjection) RETURN count(p) AS n").single().get("n").asLong());
    }
  }
  @Test void pendingProjectionAndMissingLifecycleLockRefuseRestoration() {
    try (var session = database.session()) {
      session.run("CREATE (:CedarVersionProjection {resourceId:'a', content:'pending user content'})").consume();
      assertTrue(session.run(query, Map.of("rows", List.of(row("a")))).list().isEmpty());
      session.run("MATCH (p:CedarVersionProjection) DELETE p").consume();
      session.run("MATCH (lock:CedarVersionLock) DELETE lock").consume();
      assertTrue(session.run(query, Map.of("rows", List.of(row("a")))).list().isEmpty());
      assertEquals("admin", session.run("MATCH (a:Artifact {_id:'a'}) RETURN a.oslc_modifiedBy AS author").single().get("author").asString());
    }
  }
  @Test void duplicateAndOversizedBatchesCannotChangeAnArtifact() {
    try (var session = database.session()) {
      assertTrue(session.run(query, Map.of("rows", List.of(row("a"), row("a")))).list().isEmpty());
      assertTrue(session.run(query, Map.of("rows", Collections.nCopies(101, row("a")))).list().isEmpty());
      assertEquals("admin", session.run("MATCH (a:Artifact {_id:'a'}) RETURN a.oslc_modifiedBy AS author").single().get("author").asString());
    }
  }

}
