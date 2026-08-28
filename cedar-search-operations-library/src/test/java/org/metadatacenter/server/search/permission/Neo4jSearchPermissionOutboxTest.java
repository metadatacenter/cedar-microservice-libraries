package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.harness.Neo4jBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
