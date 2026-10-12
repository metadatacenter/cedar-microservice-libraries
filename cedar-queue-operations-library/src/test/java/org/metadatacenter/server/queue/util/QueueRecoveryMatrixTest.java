package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.metadatacenter.config.CacheServerPersistent;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Failed transfers and recovery must preserve accepted work; uses one isolated Redis fixture. */
@Timeout(30)
class QueueRecoveryMatrixTest {
  private static EmbeddedRedis redis;
  private static CacheServerPersistent config;

  @BeforeAll static void start() {
    redis = EmbeddedRedis.start();
    config = QueueTestConfig.onPort(redis.port());
  }
  @AfterAll static void stop() { redis.close(); }
  @BeforeEach void clear() {
    try (var jedis = connection()) { jedis.flushDB(); }
  }
  private Jedis connection() { return new Jedis("127.0.0.1", redis.port()); }

  private QueueService service(String kind) {
    return switch (kind) {
      case "blocking" -> new PermissionQueueService(config);
      case "clone" -> new CloneInstancesQueueService(config);
      case "batch" -> new QueueServiceWithNonBlockingQueue(config, QueueService.VALUERECOMMENDER_QUEUE_ID) {};
      default -> throw new IllegalArgumentException(kind);
    };
  }
  private String pending(String kind) {
    return QueueTestConfig.queueName(switch (kind) {
      case "blocking" -> QueueService.SEARCH_PERMISSION_QUEUE_ID;
      case "clone" -> QueueService.CLONE_INSTANCES_QUEUE_ID;
      default -> QueueService.VALUERECOMMENDER_QUEUE_ID;
    });
  }
  private void initialize(QueueService service) {
    if (service instanceof QueueServiceWithBlockingQueue blocking) blocking.initializeBlockingQueue();
  }
  private List<String> claim(QueueService service) {
    if (service instanceof QueueServiceWithBlockingQueue blocking) return blocking.claimAvailableMessages(1);
    return ((QueueServiceWithNonBlockingQueue) service).claimMessages(1);
  }
  private boolean park(QueueService service, String payload) {
    if (service instanceof QueueServiceWithBlockingQueue blocking) return blocking.deadLetter(payload);
    return ((QueueServiceWithNonBlockingQueue) service).deadLetter(payload);
  }

  @ParameterizedTest(name = "{0}: {1} transfer failure retains source")
  @CsvSource({"blocking,claim", "clone,claim", "batch,claim",
      "blocking,recover", "clone,recover", "batch,recover",
      "blocking,deadLetter", "clone,deadLetter", "batch,deadLetter"})
  void aRejectedTransferMustNotConsumeItsSource(String kind, String operation) {
    var service = service(kind);
    String pending = pending(kind), processing = pending + QueueService.PROCESSING_SUFFIX;
    String deadLetter = pending + QueueService.DEAD_LETTER_SUFFIX;
    try (var jedis = connection()) {
      initialize(service);
      jedis.rpush(pending, "message");
      String source;
      if (operation.equals("claim")) {
        jedis.set(processing, "injected non-list destination");
        assertThrows(JedisDataException.class, () -> claim(service));
        source = pending;
      } else {
        assertEquals(List.of("message"), claim(service));
        source = processing;
        if (operation.equals("recover")) {
          jedis.set(pending, "injected non-list destination");
          assertThrows(JedisDataException.class, () -> {
            if (service instanceof QueueServiceWithBlockingQueue) initialize(service);
            else claim(service);
          });
        } else {
          if (service instanceof CloneInstancesQueueService clone) assertTrue(clone.beginExecution("message"));
          jedis.set(deadLetter, "injected non-list destination");
          assertFalse(park(service, "message"));
        }
      }
      assertEquals(List.of("message"), jedis.lrange(source, 0, -1),
          "a refused transfer must leave the accepted payload available for recovery");
      // Repair only the injected bad destination, then prove the original message still moves.
      jedis.del(operation.equals("claim") ? processing : operation.equals("recover") ? pending : deadLetter);
      if (operation.equals("deadLetter")) {
        assertTrue(park(service, "message"));
        assertEquals(List.of("message"), jedis.lrange(deadLetter, 0, -1));
      } else {
        if (operation.equals("recover")) initialize(service);
        assertEquals(List.of("message"), claim(service));
      }

    } finally { service.close(); }
  }

  @Test void aRecoveredAndCompletedCloneCannotBeStartedByItsEarlierClaimant() {
    var first = new CloneInstancesQueueService(config);
    var replacement = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      first.initializeBlockingQueue();
      jedis.rpush(pending, "same-event");
      assertEquals(List.of("same-event"), first.claimAvailableMessages(1));
      // First process pauses between claiming and establishing its execution fence.
      replacement.initializeBlockingQueue();
      assertEquals(List.of("same-event"), replacement.claimAvailableMessages(1));
      assertTrue(replacement.beginExecution("same-event"));
      assertTrue(replacement.acknowledge("same-event"));
      assertEquals(0, replacement.inFlightCount());
      assertFalse(first.beginExecution("same-event"),
          "the stale claimant must not repeat a clone already completed by its replacement");
    } finally { first.close(); replacement.close(); }
  }

  @Test void sequentialCrashRecoveryPreservesFifoAndDuplicatePayloadMultiplicity() {
    var original = new PermissionQueueService(config);
    var recovered = new PermissionQueueService(config);
    String pending = pending("blocking");
    try (var jedis = connection()) {
      original.initializeBlockingQueue();
      jedis.rpush(pending, "same", "same", "later");
      assertEquals(List.of("same", "same"), original.claimAvailableMessages(2));
      original.close();
      recovered.initializeBlockingQueue();
      assertEquals(List.of("same", "same", "later"), recovered.claimAvailableMessages(3));
      assertTrue(recovered.acknowledge("same"));
      assertEquals(2, recovered.inFlightCount());
      assertTrue(recovered.acknowledge("same"));
      assertTrue(recovered.acknowledge("later"));
      assertEquals(0, recovered.inFlightCount());
    } finally { recovered.close(); }
  }

  @ParameterizedTest(name = "stale {0} after replacement {1}")
  @CsvSource({"begin,claimed", "ack,claimed", "park,claimed",
      "begin,started", "ack,started", "park,started",
      "begin,completed", "ack,completed", "park,completed",
      "begin,replayed", "ack,replayed", "park,replayed"})
  void staleCallbacksCannotExecuteOrChangeAReplacement(String operation, String phase) {
    var first = new CloneInstancesQueueService(config);
    var next = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      first.initializeBlockingQueue();
      jedis.rpush(pending, "event");
      assertEquals(List.of("event"), first.claimAvailableMessages(1));
      next.initializeBlockingQueue();
      assertEquals(List.of("event"), next.claimAvailableMessages(1));
      if (!phase.equals("claimed")) assertTrue(next.beginExecution("event"));
      if (phase.equals("completed") || phase.equals("replayed")) assertTrue(next.acknowledge("event"));
      if (phase.equals("replayed")) {
        jedis.rpush(pending, "event");
        assertEquals(List.of("event"), next.claimAvailableMessages(1));
      }
      var keys = List.of(pending, pending + QueueService.PROCESSING_SUFFIX,
          pending + QueueService.DEAD_LETTER_SUFFIX, pending + ":claims", pending + ":executions");
      var before = keys.stream().map(jedis::dump).toList();
      switch (operation) {
        case "begin" -> assertFalse(first.beginExecution("event"));
        case "ack" -> assertTrue(first.acknowledge("event"), "stale completion must not spin forever");
        case "park" -> assertFalse(first.deadLetter("event"));
        default -> fail(operation);
      }
      for (int i = 0; i < keys.size(); i++) assertArrayEquals(before.get(i), jedis.dump(keys.get(i)), keys.get(i));
      if (phase.equals("claimed") || phase.equals("replayed")) assertTrue(next.beginExecution("event"));
    } finally { first.close(); next.close(); }
  }

  @ParameterizedTest(name = "clone {0}, invalid {1}")
  @CsvSource({"ack,pending", "ack,executions", "ack,claims",
      "park,pending", "park,executions", "park,claims"})
  void cloneFinalizationValidatesEveryKeyBeforeRemovingWork(String operation, String fault) {
    var queue = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      queue.initializeBlockingQueue();
      jedis.rpush(pending, "event");
      assertEquals(List.of("event"), queue.claimAvailableMessages(1));
      assertTrue(queue.beginExecution("event"));
      var claims = jedis.hgetAll(pending + ":claims");
      String badKey = fault.equals("pending") ? pending : pending + ":" + fault;
      jedis.set(badKey, "injected invalid type");
      var keys = List.of(pending, pending + QueueService.PROCESSING_SUFFIX,
          pending + QueueService.DEAD_LETTER_SUFFIX, pending + ":claims", pending + ":executions");
      var before = keys.stream().map(jedis::dump).toList();
      if (operation.equals("ack")) assertThrows(JedisDataException.class, () -> queue.acknowledge("event"));
      else assertFalse(queue.deadLetter("event"));
      for (int i = 0; i < keys.size(); i++) assertArrayEquals(before.get(i), jedis.dump(keys.get(i)), keys.get(i));
      jedis.del(badKey);
      if (fault.equals("executions")) jedis.hset(badKey, "event", "started");
      if (fault.equals("claims")) jedis.hset(badKey, claims);
      assertTrue(operation.equals("ack") ? queue.acknowledge("event") : queue.deadLetter("event"));
      assertEquals(0, queue.inFlightCount());
      assertEquals(0, jedis.hlen(pending + ":claims"));
      assertEquals(0, jedis.hlen(pending + ":executions"));
    } finally { queue.close(); }
  }

  @Test void legacyInFlightExecutionIsParkedWithoutRepeatingIt() {
    var queue = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      // Old workers did not write claim receipts; their started fences and raw lists must survive upgrade.
      jedis.rpush(pending + QueueService.PROCESSING_SUFFIX, "legacy");
      jedis.hset(pending + ":executions", "legacy", "started");
      queue.initializeBlockingQueue();
      assertEquals(List.of("legacy"), queue.claimAvailableMessages(1));
      assertFalse(queue.beginExecution("legacy"));
      assertTrue(queue.deadLetter("legacy"));
      assertEquals(List.of("legacy"), jedis.lrange(queue.getDeadLetterQueueName(), 0, -1));
    } finally { queue.close(); }
  }

  @Test void lostClaimReplyCanBeRecoveredWithoutAnUnownedExecution() {
    var queue = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      queue.initializeBlockingQueue();
      jedis.rpush(pending, "event");
      // Model Redis accepting a claim whose caller then dies before receiving its reply.
      assertEquals(List.of("event"), queue.claimAvailableMessages(1));
      queue.initializeBlockingQueue();
      assertFalse(queue.beginExecution("event"), "reinitializing discards receipts from the previous connection");
      assertEquals(List.of("event"), queue.claimAvailableMessages(1));
      assertTrue(queue.beginExecution("event"));
      assertTrue(queue.acknowledge("event"));
      assertTrue(queue.acknowledge("event"));
      assertEquals(0, queue.inFlightCount());
    } finally { queue.close(); }
  }

  @ParameterizedTest(name = "restart after failure {1} {0}")
  @CsvSource({"claim,before", "claim,after", "begin,before", "begin,after",
      "ack,before", "ack,after", "park,before", "park,after"})
  void recoveryDistinguishesUnstartedCompletedAndUncertainWork(String operation, String boundary) {
    var original = new CloneInstancesQueueService(config);
    var recovered = new CloneInstancesQueueService(config);
    String pending = pending("clone");
    try (var jedis = connection()) {
      original.initializeBlockingQueue();
      jedis.rpush(pending, "event");
      if (!operation.equals("claim")) assertEquals(List.of("event"), original.claimAvailableMessages(1));
      if (operation.equals("ack") || operation.equals("park")) assertTrue(original.beginExecution("event"));
      // Stop at either side of the Redis operation. "After" models a reply lost after Redis
      // committed; recovery cannot infer successful handling from what the caller observed.
      assertThrows(IllegalStateException.class, () -> {
        if (boundary.equals("before")) throw new IllegalStateException("request not delivered");
        switch (operation) {
          case "claim" -> assertEquals(List.of("event"), original.claimAvailableMessages(1));
          case "begin" -> assertTrue(original.beginExecution("event"));
          case "ack" -> assertTrue(original.acknowledge("event"));
          case "park" -> assertTrue(original.deadLetter("event"));
          default -> fail(operation);
        }
        throw new IllegalStateException("reply lost");
      });
      original.close();
      recovered.initializeBlockingQueue();
      boolean finalized = boundary.equals("after") && (operation.equals("ack") || operation.equals("park"));
      assertEquals(finalized ? List.of() : List.of("event"), recovered.claimAvailableMessages(1));
      boolean uncertain = !finalized && (operation.equals("ack") || operation.equals("park")
          || operation.equals("begin") && boundary.equals("after"));
      if (!finalized) {
        assertEquals(!uncertain, recovered.beginExecution("event"));
        assertTrue(uncertain ? recovered.deadLetter("event") : recovered.acknowledge("event"));
      }
      assertEquals(uncertain || operation.equals("park") && finalized ? List.of("event") : List.of(),
          jedis.lrange(recovered.getDeadLetterQueueName(), 0, -1));
      assertEquals(0, recovered.inFlightCount());
      assertEquals(0, recovered.messageCount());
      assertEquals(0, jedis.hlen(pending + ":claims"));
      assertEquals(0, jedis.hlen(pending + ":executions"));
    } finally { original.close(); recovered.close(); }
  }
}
