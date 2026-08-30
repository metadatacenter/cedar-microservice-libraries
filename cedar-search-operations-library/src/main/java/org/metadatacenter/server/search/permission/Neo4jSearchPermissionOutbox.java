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
import org.neo4j.driver.Transaction;
import org.neo4j.driver.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private static final Logger log = LoggerFactory.getLogger(Neo4jSearchPermissionOutbox.class);
  private static final String LABEL = "CedarSearchPermissionOutbox";
  private static final String DEAD_LETTER_LABEL = "CedarSearchPermissionOutboxDeadLetter";
  private static final String RELAY_LOCK_LABEL = "CedarSearchPermissionOutboxRelayLock";
  private static final String RELAY_LOCK_NAME = "search-permission-relay";
  private static final String RELAY_LOCK_CONSTRAINT = "cedar_search_permission_outbox_relay_lock_name";
  private static final List<String> VALID_EVENT_TYPES = Arrays.stream(SearchPermissionQueueEventType.values())
      .map(Enum::name)
      .toList();

  private final Driver driver;
  private volatile boolean relayLockReady;

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

  private synchronized void ensureRelayLock() {
    if (relayLockReady) {
      return;
    }
    String constraintQuery = "CREATE CONSTRAINT " + RELAY_LOCK_CONSTRAINT + " IF NOT EXISTS "
        + "FOR (lock:" + RELAY_LOCK_LABEL + ") REQUIRE lock.name IS UNIQUE";
    String lockQuery = "MERGE (:" + RELAY_LOCK_LABEL + " {name: $name})";
    try (Session session = driver.session()) {
      session.run(constraintQuery).consume();
      session.run(lockQuery, Map.of("name", RELAY_LOCK_NAME)).consume();
      relayLockReady = true;
    }
  }

  private static void acquireRelayLock(Transaction tx) {
    // The uniqueness constraint installed by ensureRelayLock makes this a single cross-process
    // mutex. Updating the node acquires Neo4j's exclusive write lock until the transaction commits.
    String query = "MATCH (lock:" + RELAY_LOCK_LABEL + " {name: $name}) "
        + "SET lock.version = coalesce(lock.version, 0) + 1";
    tx.run(query, Map.of("name", RELAY_LOCK_NAME)).consume();
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
    ensureRelayLock();
    String quarantineQuery = "MATCH (e:" + LABEL + ") "
        + "WHERE e.outboxId IS NULL OR e.resourceId IS NULL OR e.eventType IS NULL "
        + "OR NOT e.eventType IN $validEventTypes "
        + "WITH e, CASE "
        + "WHEN e.outboxId IS NULL THEN 'missing outboxId' "
        + "WHEN e.resourceId IS NULL THEN 'missing resourceId' "
        + "WHEN e.eventType IS NULL THEN 'missing eventType' "
        + "ELSE 'unknown eventType: ' + toString(e.eventType) END AS reason "
        + "SET e:" + DEAD_LETTER_LABEL + ", e.deadLetterReason = reason, "
        + "e.deadLetteredAtTS = $deadLetteredAtTS "
        + "REMOVE e:" + LABEL + " "
        + "RETURN coalesce(toString(e.outboxId), '<missing>') AS outboxId, reason";
    String query = "MATCH (e:" + LABEL + ") "
        + "RETURN toString(e.outboxId) AS outboxId, toString(e.resourceId) AS resourceId, "
        + "toString(e.eventType) AS eventType "
        + "ORDER BY e.createdAtTS, e.outboxId LIMIT $limit";
    try (Session session = driver.session()) {
      return session.writeTransaction(tx -> {
        acquireRelayLock(tx);
        List<Record> quarantined = tx.run(quarantineQuery, Map.of(
            "validEventTypes", VALID_EVENT_TYPES,
            "deadLetteredAtTS", TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))).list();
        if (!quarantined.isEmpty()) {
          List<String> descriptions = quarantined.stream()
              .map(record -> record.get("outboxId").asString() + " (" + record.get("reason").asString() + ")")
              .toList();
          log.error("Quarantined {} malformed search-permission outbox event(s): {}",
              descriptions.size(), descriptions);
        }
        List<Entry> entries = new ArrayList<>();
        for (Record record : tx.run(query, Map.of("limit", limit)).list()) {
          entryFromRecord(record).ifPresent(entries::add);
        }
        return entries;
      });
    }
  }

  static Optional<Entry> entryFromRecord(Record record) {
    Value outboxId = record.get("outboxId");
    Value resourceId = record.get("resourceId");
    Value eventType = record.get("eventType");
    if (outboxId.isNull() || resourceId.isNull() || eventType.isNull()) {
      // Resource and group servers relay the same durable outbox. If one removes an acknowledged
      // node while the other is materializing its projection, Neo4j can return null properties to
      // the losing reader. The event was already delivered by the winner, so there is no work to
      // retry and this must not be treated as a malformed persisted record.
      log.debug("Skipping a search-permission outbox event concurrently acknowledged by another relay");
      return Optional.empty();
    }
    String eventTypeName = eventType.asString();
    if (!VALID_EVENT_TYPES.contains(eventTypeName)) {
      // A malformed event committed after this transaction's quarantine pass will be parked by the
      // next pass. Do not let that narrow race abort the valid entries already selected here.
      log.warn("Skipping search-permission outbox event {} with unknown event type {}; "
          + "the next relay pass will quarantine it", outboxId.asString(), eventTypeName);
      return Optional.empty();
    }
    return Optional.of(new Entry(outboxId.asString(),
        new SearchPermissionQueueEvent(resourceId.asString(),
            SearchPermissionQueueEventType.valueOf(eventTypeName))));
  }

  @Override
  public void remove(String outboxId) {
    ensureRelayLock();
    String query = "MATCH (e:" + LABEL + " {outboxId: $outboxId}) DELETE e";
    try (Session session = driver.session()) {
      session.writeTransaction(tx -> {
        acquireRelayLock(tx);
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
