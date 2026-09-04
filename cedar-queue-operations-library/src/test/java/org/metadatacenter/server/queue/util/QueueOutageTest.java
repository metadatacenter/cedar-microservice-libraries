package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to the producer side when Redis is not there.
 * <p>
 * Enqueueing is best-effort: it must never fail the request that produced the event, every drop
 * must be counted, and the service must come back on its own once Redis does - a queue outage is
 * supposed to suspend enqueueing, not disable it for the life of the process. These run against a
 * real server that is genuinely stopped and restarted, because a stub cannot reproduce a pool full
 * of connections to a server that has gone away.
 */
@Timeout(60)
class QueueOutageTest {

  private static SearchPermissionQueueEvent event(String id) {
    return new SearchPermissionQueueEvent(id, SearchPermissionQueueEventType.RESOURCE_MOVED);
  }

  @Test
  void enqueueingToAnUnreachableQueueDoesNotFailTheCaller() {
    CacheServerPersistent config = QueueTestConfig.onPort(EmbeddedRedis.freePort());
    PermissionQueueService queue = new PermissionQueueService(config);
    try {
      assertDoesNotThrow(() -> queue.enqueueEvent(event("dropped-1")),
          "a queue outage must not propagate into the request that produced the event");
      assertFalse(queue.enqueueEvent(event("dropped-2")),
          "the durable producer needs an explicit signal that Redis did not accept the event");
    } finally {
      queue.close();
    }
  }

  @Test
  void everyDropIsCounted() {
    CacheServerPersistent config = QueueTestConfig.onPort(EmbeddedRedis.freePort());
    PermissionQueueService queue = new PermissionQueueService(config);
    try {
      queue.enqueueEvent(event("a"));
      queue.enqueueEvent(event("b"));
      queue.enqueueEvent(event("c"));

      assertEquals(3, queue.getDroppedEventCount());
    } finally {
      queue.close();
    }
  }

  @Test
  void nothingIsDroppedWhileTheQueueIsReachable() {
    try (EmbeddedRedis redis = EmbeddedRedis.start()) {
      PermissionQueueService queue = new PermissionQueueService(QueueTestConfig.onPort(redis.port()));
      try {
        assertTrue(queue.enqueueEvent(event("delivered")));

        assertEquals(0, queue.getDroppedEventCount());
        queue.initializeBlockingQueue();
        assertEquals(1, queue.messageCount(), "the event should be on the queue");
      } finally {
        queue.close();
      }
    }
  }

  @Test
  void dropCountsAreKeptPerService() {
    CacheServerPersistent config = QueueTestConfig.onPort(EmbeddedRedis.freePort());
    PermissionQueueService one = new PermissionQueueService(config);
    PermissionQueueService two = new PermissionQueueService(config);
    try {
      one.enqueueEvent(event("a"));
      one.enqueueEvent(event("b"));
      two.enqueueEvent(event("c"));

      assertEquals(2, one.getDroppedEventCount());
      assertEquals(1, two.getDroppedEventCount(), "one service's outage is not another's");
    } finally {
      one.close();
      two.close();
    }
  }

  /**
   * The outage ends. The service holds a pool whose connections all point at a server that went
   * away, so recovering means those connections are discarded rather than handed back out. Without
   * that, a single outage would disable enqueueing until the process restarted.
   */
  @Test
  void theServiceRecoversWhenTheQueueComesBack() {
    // start() picks a free port and retries if another process wins the race for it; startOn() with
    // a port that nothing has bound yet has no such second chance, and lost that race often enough
    // to fail a build over an unrelated change.
    EmbeddedRedis redis = EmbeddedRedis.start();
    int port = redis.port();
    PermissionQueueService queue = new PermissionQueueService(QueueTestConfig.onPort(port));
    try {
      queue.enqueueEvent(event("before-the-outage"));
      assertEquals(0, queue.getDroppedEventCount(), "nothing should drop while Redis is up");

      redis.stop();
      queue.enqueueEvent(event("during-the-outage"));
      assertEquals(1, queue.getDroppedEventCount(), "the event sent during the outage is dropped");

      redis = EmbeddedRedis.restartOn(port);
      queue.enqueueEvent(event("after-the-outage"));

      assertEquals(1, queue.getDroppedEventCount(),
          "once Redis is back, enqueueing should succeed again rather than keep dropping");
      try (Jedis jedis = new Jedis("127.0.0.1", port)) {
        assertEquals(1, jedis.llen(QueueTestConfig.queueName(QueueService.SEARCH_PERMISSION_QUEUE_ID)),
            "the post-outage event should be on the queue");
      }
    } finally {
      queue.close();
      redis.stop();
    }
  }
}
