package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoDocumentStoreHealthCheckTest {

  @Test
  void healthyOnlyWhenMongoHeartbeatIsConnected() {
    ObjectNode connected = JsonNodeFactory.instance.objectNode().put("storageServerConnection", true);
    HealthCheck.Result result = new MongoDocumentStoreHealthCheck(() -> connected).execute();
    assertTrue(result.isHealthy());
  }

  @Test
  void unhealthyWhenMongoHeartbeatReportsFailure() {
    ObjectNode disconnected = JsonNodeFactory.instance.objectNode()
        .put("storageServerConnection", false)
        .put("storageServerException", "connection refused");
    HealthCheck.Result result = new MongoDocumentStoreHealthCheck(() -> disconnected).execute();
    assertFalse(result.isHealthy());
  }
}
