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
 * The non-blocking queue service against a real Redis. This side drains a whole batch per poll
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
  void aPollDrainsEverythingInTheOrderItWasQueued() {
    queue.enqueue("one");
    queue.enqueue("two");
    queue.enqueue("three");

    assertEquals(List.of("one", "two", "three"), queue.getAllMessages());
  }

  @Test
  void aPollLeavesTheQueueEmpty() {
    queue.enqueue("only");

    assertEquals(List.of("only"), queue.getAllMessages());
    assertEquals(0, queue.messageCount(), "a drained queue holds nothing");
    assertEquals(List.of(), queue.getAllMessages(), "a second poll finds nothing left");
  }

  @Test
  void anEmptyQueuePollsToAnEmptyListRatherThanBlocking() {
    assertEquals(List.of(), queue.getAllMessages());
  }

  @Test
  void messageCountReportsWhatIsQueuedWithoutConsumingIt() {
    queue.enqueue("a");
    queue.enqueue("b");

    assertEquals(2, queue.messageCount());
    assertEquals(2, queue.messageCount(), "counting must not consume");
    assertEquals(2, queue.getAllMessages().size());
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
      queue.getAllMessages();
    }

    assertEquals(0, queue.pool.getNumActive(), "no connection may be left checked out between polls");
    assertTrue(queue.pool.getNumIdle() > 0, "the connections should be back in the pool, not discarded");
  }
}
