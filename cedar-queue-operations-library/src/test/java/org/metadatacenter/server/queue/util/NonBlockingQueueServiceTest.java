package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.config.CacheServerPersistent;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The non-blocking queue service against a real Redis. This side claims a bounded batch per poll
 * instead of waiting on a dequeue, and it borrows a connection per poll rather than holding one for
 * the life of the service - the property that keeps a Redis outage from breaking the consumer
 * permanently. Both are checked here.
 */
@Timeout(30)
class NonBlockingQueueServiceTest {

  /**
   * The library ships no concrete non-blocking service - the value-recommender one lives in its own
   * library - so the test supplies the minimal subclass, plus the producer side its consumers get
   * from elsewhere.
   */
  private static final class TestNonBlockingQueueService extends QueueServiceWithNonBlockingQueue {

    TestNonBlockingQueueService(CacheServerPersistent cacheConfig) {
      super(cacheConfig, VALUERECOMMENDER_QUEUE_ID);
    }

    void enqueue(String message) {
      try (Jedis jedis = pool.getResource()) {
        jedis.rpush(queueName, message);
      }
    }
  }

  private static EmbeddedRedis redis;
  private static CacheServerPersistent config;

  private TestNonBlockingQueueService queue;

  @BeforeAll
  static void startRedis() {
    redis = EmbeddedRedis.start();
    config = QueueTestConfig.onPort(redis.port());
  }

  @AfterAll
  static void stopRedis() {
    redis.close();
  }

  @BeforeEach
  void setUp() {
    queue = new TestNonBlockingQueueService(config);
    try (Jedis jedis = new Jedis("127.0.0.1", redis.port())) {
      jedis.flushAll();
    }
  }

  @AfterEach
  void tearDown() {
    queue.close();
  }

  @Test
  void aPollClaimsOnlyTheBoundedBatchInQueueOrder() {
    queue.enqueue("one");
    queue.enqueue("two");
    queue.enqueue("three");

    assertEquals(List.of("one", "two"), queue.claimMessages(2));
    assertEquals(1, queue.messageCount());
    assertEquals(2, queue.inFlightCount());
  }

  @Test
  void anAcknowledgedMessageIsNotDeliveredAgain() {
    queue.enqueue("only");

    assertEquals(List.of("only"), queue.claimMessages(10));
    assertTrue(queue.acknowledge("only"));
    assertEquals(List.of(), queue.claimMessages(10));
    assertEquals(0, queue.inFlightCount());
  }

  @Test
  void anUnacknowledgedMessageIsRecoveredBeforeNewerWork() {
    queue.enqueue("first");
    assertEquals(List.of("first"), queue.claimMessages(10));
    queue.enqueue("second");

    assertEquals(List.of("first", "second"), queue.claimMessages(10));
  }

  @Test
  void anEmptyQueuePollsToAnEmptyListRatherThanBlocking() {
    assertEquals(List.of(), queue.claimMessages(10));
  }

  @Test
  void messageCountReportsWhatIsQueuedWithoutConsumingIt() {
    queue.enqueue("a");
    queue.enqueue("b");

    assertEquals(2, queue.messageCount());
    assertEquals(2, queue.messageCount(), "counting must not consume");
    assertEquals(2, queue.claimMessages(10).size());
  }

  /**
   * The class holds no connection between polls by design: an unreachable Redis then costs the poll
   * that hit it and nothing more. If a borrowed connection were ever not returned, the pool would
   * bleed one per poll and eventually block forever.
   */
  @Test
  void everyPollReturnsItsConnectionToThePool() {
    for (int i = 0; i < 30; i++) {
      queue.enqueue("message-" + i);
      queue.messageCount();
      List<String> claimed = queue.claimMessages(10);
      claimed.forEach(queue::acknowledge);
    }

    assertEquals(0, queue.pool.getNumActive(), "no connection may be left checked out between polls");
    assertTrue(queue.pool.getNumIdle() > 0, "the connections should be back in the pool, not discarded");
  }

  @Test
  void anUnprocessableClaimCanBeMovedAtomicallyToDeadLetter() {
    queue.enqueue("bad");
    assertEquals(List.of("bad"), queue.claimMessages(10));

    assertTrue(queue.deadLetter("bad"));
    assertEquals(0, queue.inFlightCount());
    assertEquals(1, queue.deadLetterCount());
  }
}
