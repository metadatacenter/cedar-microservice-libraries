package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.metadatacenter.util.json.JsonMapper;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocking queue services against a real Redis: what a producer pushes is what a consumer pops,
 * in order, off the queue the configuration names.
 * <p>
 * Every test has a timeout because the consumer side calls BLPOP with no deadline. A regression
 * that leaves the queue empty would otherwise hang the build rather than fail it, which is far
 * worse in CI than a red test.
 */
@Timeout(30)
class BlockingQueueServiceTest {

  private static EmbeddedRedis redis;
  private static CacheServerPersistent config;

  private PermissionQueueService permissionQueue;

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
    permissionQueue = new PermissionQueueService(config);
    flush();
  }

  @AfterEach
  void tearDown() {
    flush();
    permissionQueue.close();
  }

  /** Each test starts from an empty queue, so counts and ordering mean what they say. */
  private void flush() {
    try (Jedis jedis = new Jedis("127.0.0.1", redis.port())) {
      jedis.flushAll();
    }
  }

  @Test
  void anEnqueuedEventComesBackFromTheBlockingPop() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("artifact-1", SearchPermissionQueueEventType.RESOURCE_MOVED));

    permissionQueue.initializeBlockingQueue();
    List<String> popped = permissionQueue.waitForMessages();

    assertNotNull(popped, "BLPOP should return a message, not time out");
    assertEquals(QueueTestConfig.queueName(QueueService.SEARCH_PERMISSION_QUEUE_ID), popped.get(0),
        "BLPOP reports the queue it popped from as the first element");

    SearchPermissionQueueEvent event = readEvent(popped.get(1));
    assertEquals("artifact-1", event.getId());
    assertEquals(SearchPermissionQueueEventType.RESOURCE_MOVED, event.getEventType());
  }

  @Test
  void eventsComeBackInTheOrderTheyWereEnqueued() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("first", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("second", SearchPermissionQueueEventType.FOLDER_MOVED));
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("third", SearchPermissionQueueEventType.GROUP_DELETED));

    permissionQueue.initializeBlockingQueue();

    assertEquals("first", readEvent(permissionQueue.waitForMessages().get(1)).getId());
    assertEquals("second", readEvent(permissionQueue.waitForMessages().get(1)).getId());
    assertEquals("third", readEvent(permissionQueue.waitForMessages().get(1)).getId());
  }

  @Test
  void messageCountReflectsWhatIsStillWaiting() {
    permissionQueue.initializeBlockingQueue();
    assertEquals(0, permissionQueue.messageCount(), "a fresh queue holds nothing");

    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("a", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("b", SearchPermissionQueueEventType.RESOURCE_MOVED));
    assertEquals(2, permissionQueue.messageCount());

    permissionQueue.waitForMessages();
    assertEquals(1, permissionQueue.messageCount(), "a consumed message leaves the queue");
  }

  /**
   * A consumer re-initializes after a failure. The replaced connection has to be released, or a
   * queue that flaps leaks one pooled connection per retry until the pool is exhausted.
   */
  @Test
  void reinitializingDoesNotLeakTheReplacedConnection() {
    for (int i = 0; i < 20; i++) {
      permissionQueue.initializeBlockingQueue();
    }

    assertEquals(1, permissionQueue.pool.getNumActive(),
        "each re-initialization should replace the held connection, not add one");
  }

  @Test
  void closingReleasesTheHeldConnectionAndThePool() {
    permissionQueue.initializeBlockingQueue();
    permissionQueue.close();

    assertTrue(permissionQueue.pool.isClosed(), "close should shut the pool down");
    assertThrows(Exception.class, () -> permissionQueue.pool.getResource(),
        "a closed pool should not hand out connections");

    // The @AfterEach close is harmless on an already-closed service, which is itself worth knowing:
    // a Dropwizard managed object can be stopped after a failed start
    permissionQueue = new PermissionQueueService(config);
  }

  /**
   * Two services on one Redis must not read each other's traffic. The queue name is the only thing
   * separating them, so a mistake in that lookup would silently cross the streams.
   */
  @Test
  void eachQueueIdAddressesItsOwnQueue() {
    CloneInstancesQueueService cloneQueue = new CloneInstancesQueueService(config);
    try {
      permissionQueue.enqueueEvent(
          new SearchPermissionQueueEvent("only-permission", SearchPermissionQueueEventType.RESOURCE_MOVED));

      permissionQueue.initializeBlockingQueue();
      cloneQueue.initializeBlockingQueue();

      assertEquals(1, permissionQueue.messageCount(), "the permission queue holds the event");
      assertEquals(0, cloneQueue.messageCount(), "the clone-instances queue must not see it");
    } finally {
      cloneQueue.close();
    }
  }

  private static SearchPermissionQueueEvent readEvent(String json) {
    try {
      return JsonMapper.MAPPER.readValue(json, SearchPermissionQueueEvent.class);
    } catch (Exception e) {
      throw new IllegalStateException("The queued message did not deserialize: " + json, e);
    }
  }
}
