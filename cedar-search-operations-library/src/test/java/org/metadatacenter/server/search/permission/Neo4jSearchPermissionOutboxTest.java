package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
class Neo4jSearchPermissionOutboxTest {

  // One harness serves every test, since booting one takes several seconds. The restart tests
  // restart the outbox's client, which a new driver does, and never need a new database.
  private static Neo4j neo4j;

  @BeforeAll
  static void startDatabase() {
    // Closing the harness shuts Bolt's Netty event-loop group down gracefully: every loop closes
    // its connections, then all of them wait out a five-second quiet period together, so a close
    // took about six seconds. The connections are already closed when the period starts, so a zero
    // period changes nothing a client can observe, and the close takes under one second.
    neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(GraphDatabaseInternalSettings.netty_server_shutdown_quiet_period, 0).build();
  }

  /**
   * Gives each test the graph of a freshly booted harness. Its outbox finds no nodes, and none of
   * the constraints or indexes an earlier test's outbox created, so the test asserting that the
   * outbox creates its indexes still proves it.
   */
  @BeforeEach
  void blankDatabase() {
    GraphDatabaseService database = neo4j.defaultDatabaseService();
    database.executeTransactionally("MATCH (n) DETACH DELETE n");
    for (String constraint : names(database, "SHOW CONSTRAINTS YIELD name RETURN name")) {
      database.executeTransactionally("DROP CONSTRAINT `" + constraint + "`");
    }
    for (String index : names(database, "SHOW INDEXES YIELD name, type WHERE type <> 'LOOKUP' RETURN name")) {
      database.executeTransactionally("DROP INDEX `" + index + "`");
    }
  }

  @AfterAll
  static void stopDatabase() {
    if (neo4j != null) {
      neo4j.close();
    }
  }

  private static List<String> names(GraphDatabaseService database, String query) {
    return database.executeTransactionally(query, Map.of(),
        result -> result.stream().map(row -> (String) row.get("name")).toList());
  }

  @Test
  void eventsSurviveAClientRestartAndDisappearOnlyAfterAcknowledgement() {
    String outboxId;
    try (var first = new Neo4jSearchPermissionOutbox(
        GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none()))) {
      outboxId = first.append(new SearchPermissionQueueEvent(
          "resource-1", SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED));
      assertEquals(1, first.count());
    }

    try (var restarted = new Neo4jSearchPermissionOutbox(
        GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none()))) {
      var pending = restarted.pending(100);
      assertEquals(1, pending.size());
      assertEquals(outboxId, pending.get(0).outboxId());
      assertEquals("resource-1", pending.get(0).event().getId());
      assertEquals(SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED,
          pending.get(0).event().getEventType());

      restarted.remove(List.of(outboxId));
      assertEquals(0, restarted.count());
    }
  }

  @Test
  void malformedEntriesAreQuarantinedWithoutBlockingValidEvents() {
    try (var seedDriver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
         var session = seedDriver.session()) {
      session.writeTransaction(tx -> {
        tx.run("CREATE (:CedarSearchPermissionOutbox {outboxId: 'missing-type', "
            + "resourceId: 'resource-missing-type', createdAtTS: 0})").consume();
        tx.run("CREATE (:CedarSearchPermissionOutbox {outboxId: 'unknown-type', "
            + "resourceId: 'resource-unknown-type', eventType: 'NOT_A_REAL_EVENT', createdAtTS: 1})")
            .consume();
        return null;
      });
    }

    try (var outbox = new Neo4jSearchPermissionOutbox(
        GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none()))) {
      String validId = outbox.append(new SearchPermissionQueueEvent(
          "resource-valid", SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED));

      var pending = outbox.pending(100);
      assertEquals(3, outbox.count(), "relay reads must not run the full quarantine scan");
      outbox.quarantineMalformed();

      assertEquals(1, pending.size());
      assertEquals(validId, pending.get(0).outboxId());
      assertEquals("resource-valid", pending.get(0).event().getId());
      assertEquals(1, outbox.count(), "only the valid active event should remain pending");
    }

    try (var inspectionDriver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
         var session = inspectionDriver.session()) {
      long deadLetters = session.readTransaction(tx -> tx.run(
              "MATCH (e:CedarSearchPermissionOutboxDeadLetter) RETURN count(e) AS count")
          .single().get("count").asLong());
      long stillActive = session.readTransaction(tx -> tx.run(
              "MATCH (e:CedarSearchPermissionOutboxDeadLetter:CedarSearchPermissionOutbox) "
                  + "RETURN count(e) AS count")
          .single().get("count").asLong());
      long reasons = session.readTransaction(tx -> tx.run(
              "MATCH (e:CedarSearchPermissionOutboxDeadLetter) "
                  + "WHERE e.deadLetterReason IS NOT NULL AND e.deadLetteredAtTS IS NOT NULL "
                  + "RETURN count(e) AS count")
          .single().get("count").asLong());
      long relayLocks = session.readTransaction(tx -> tx.run(
              "MATCH (lock:CedarSearchPermissionOutboxRelayLock "
                  + "{name: 'search-permission-relay'}) RETURN count(lock) AS count")
          .single().get("count").asLong());

      assertEquals(2, deadLetters);
      assertEquals(0, stillActive, "dead letters must not remain in the active relay label");
      assertEquals(2, reasons, "each quarantined entry should explain when and why it was parked");
      assertEquals(1, relayLocks, "relay scans and acknowledgements must share a database mutex");
    }
  }

  @Test
  void batchAcknowledgementPreservesNewEventsAndDoesNotBlockAppendOnTheRelayLock() throws Exception {
    try (var driver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
         var outbox = new Neo4jSearchPermissionOutbox(driver)) {
      String first = outbox.append(new SearchPermissionQueueEvent(
          "resource-1", SearchPermissionQueueEventType.RESOURCE_MOVED));
      String second = outbox.append(new SearchPermissionQueueEvent(
          "resource-2", SearchPermissionQueueEventType.RESOURCE_MOVED));
      assertEquals(2, outbox.pending(100).size());
      try (var session = driver.session()) {
        session.run("CALL db.awaitIndexes(30)").consume();
        var indexes = session.run("SHOW INDEXES YIELD name, state WHERE name STARTS WITH "
            + "'cedar_search_permission_outbox_' RETURN name, state").list();
        assertTrue(indexes.stream().anyMatch(r -> r.get("name").asString().equals(
            "cedar_search_permission_outbox_id") && r.get("state").asString().equals("ONLINE")));
        assertTrue(indexes.stream().anyMatch(r -> r.get("name").asString().equals(
            "cedar_search_permission_outbox_created_id") && r.get("state").asString().equals("ONLINE")));
        try (var tx = session.beginTransaction()) {
          tx.run("MATCH (lock:CedarSearchPermissionOutboxRelayLock) SET lock.version = 42").consume();
          var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
          try {
            executor.submit(() -> outbox.append(new SearchPermissionQueueEvent(
                "resource-new", SearchPermissionQueueEventType.RESOURCE_MOVED)))
                .get(3, java.util.concurrent.TimeUnit.SECONDS);
          } finally {
            executor.shutdownNow();
          }
          tx.commit();
        }
      }
      outbox.remove(List.of(first, second));
      outbox.remove(List.of(first, second)); // Another process may acknowledge the same batch.
      assertEquals(1, outbox.count());
      assertEquals("resource-new", outbox.pending(100).get(0).event().getId());
    }
  }

  @Test
  void concurrentlyAcknowledgedEntryIsIgnoredWhenNeo4jReturnsANullProjection() {
    Record record = mock(Record.class);
    when(record.get("outboxId")).thenReturn(Values.value("outbox-1"));
    when(record.get("resourceId")).thenReturn(Values.value("resource-1"));
    when(record.get("eventType")).thenReturn(Values.NULL);

    assertTrue(Neo4jSearchPermissionOutbox.entryFromRecord(record).isEmpty());
  }
}
