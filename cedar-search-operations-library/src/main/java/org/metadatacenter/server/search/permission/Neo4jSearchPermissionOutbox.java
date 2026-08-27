package org.metadatacenter.server.search.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Durable hand-off between a graph mutation and the Redis permission queue.
 *
 * <p>The node is deliberately outside the resource label hierarchy, so normal workspace and search
 * queries never see it. A relay may enqueue an entry and crash before removing it; that creates a
 * duplicate after restart, which is safe because every permission projection operation is
 * idempotent. Removing before enqueueing would create the data-loss window this outbox exists to
 * close.</p>
 */
final class Neo4jSearchPermissionOutbox implements SearchPermissionOutbox {

  private static final String LABEL = "CedarSearchPermissionOutbox";

  private final Driver driver;

  Neo4jSearchPermissionOutbox(CedarConfig cedarConfig) {
    Neo4jConfig neo4j = Neo4jConfig.fromCedarConfig(cedarConfig);
    Config.ConfigBuilder driverConfig = Config.builder();
    CedarTestRuntime.dependencyTimeoutMillis().ifPresent(timeout -> driverConfig
        .withConnectionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withConnectionAcquisitionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withMaxTransactionRetryTime(timeout, TimeUnit.MILLISECONDS));
    this.driver = GraphDatabase.driver(neo4j.getUri(),
        AuthTokens.basic(neo4j.getUserName(), neo4j.getUserPassword()), driverConfig.build());
  }

  Neo4jSearchPermissionOutbox(Driver driver) {
    this.driver = driver;
  }

  @Override
  public String append(SearchPermissionQueueEvent event) {
    String outboxId = UUID.randomUUID().toString();
    String query = "CREATE (e:" + LABEL + " {outboxId: $outboxId, resourceId: $resourceId, "
        + "eventType: $eventType, createdAt: $createdAt, createdAtTS: $createdAtTS})";
    Map<String, Object> parameters = Map.of(
        "outboxId", outboxId,
        "resourceId", event.getId(),
        "eventType", event.getEventType().name(),
        "createdAt", event.getCreatedAt(),
        "createdAtTS", event.getCreatedAtTS());
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> {
        tx.run(query, parameters).consume();
        return null;
      });
    }
    return outboxId;
  }

  @Override
  public List<Entry> pending(int limit) {
    String query = "MATCH (e:" + LABEL + ") "
        + "RETURN e.outboxId AS outboxId, e.resourceId AS resourceId, e.eventType AS eventType "
        + "ORDER BY e.createdAtTS, e.outboxId LIMIT $limit";
    try (Session session = driver.session()) {
      return session.readTransaction(tx -> {
        List<Entry> entries = new ArrayList<>();
        for (Record record : tx.run(query, Map.of("limit", limit)).list()) {
          entries.add(new Entry(record.get("outboxId").asString(),
              new SearchPermissionQueueEvent(record.get("resourceId").asString(),
                  SearchPermissionQueueEventType.valueOf(record.get("eventType").asString()))));
        }
        return entries;
      });
    }
  }

  @Override
  public void remove(String outboxId) {
    String query = "MATCH (e:" + LABEL + " {outboxId: $outboxId}) DELETE e";
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> {
        tx.run(query, Map.of("outboxId", outboxId)).consume();
        return null;
      });
    }
  }

  @Override
  public long count() {
    String query = "MATCH (e:" + LABEL + ") RETURN count(e) AS pending";
    try (Session session = driver.session()) {
      return session.readTransaction(tx -> tx.run(query).single().get("pending").asLong());
    }
  }

  @Override
  public void close() {
    driver.close();
  }
}
