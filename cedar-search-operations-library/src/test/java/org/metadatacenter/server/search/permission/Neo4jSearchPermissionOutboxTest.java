package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Values;
import org.neo4j.harness.Neo4jBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Neo4jSearchPermissionOutboxTest {

  @Test
  void eventsSurviveAClientRestartAndDisappearOnlyAfterAcknowledgement() {
    try (var neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build()) {
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

        restarted.remove(outboxId);
        assertEquals(0, restarted.count());
      }
    }
  }

  @Test
  void malformedEntriesAreQuarantinedWithoutBlockingValidEvents() {
    try (var neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build()) {
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
