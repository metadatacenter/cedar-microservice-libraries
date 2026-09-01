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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocking queue services against a real Redis: what a producer pushes is what a consumer
 * claims, in order, from the queue the configuration names.
 * <p>
 * Every test has a timeout because a regression in the claim must fail the build rather than leave
 * CI waiting indefinitely.
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

    assertNotNull(popped, "the claim should return a message, not an idle turn");
    assertEquals(QueueTestConfig.queueName(QueueService.SEARCH_PERMISSION_QUEUE_ID), popped.get(0),
        "the compatibility result reports the claimed queue as the first element");

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

  /**
   * A consumer claims a message into a processing list before handling it. The dead-letter queue
   * keeps a poison message available for inspection without returning it to the queue the consumer
   * drains.
   */
  @Test
  void aMessageThatCannotBeHandledIsKeptOnTheDeadLetterQueue() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("artifact-1", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.initializeBlockingQueue();
    String rawMessage = permissionQueue.waitForMessages().get(1);

    assertTrue(permissionQueue.deadLetter(rawMessage), "the message should be parked");

    assertEquals(1, permissionQueue.deadLetterCount(), "the dead-letter queue holds the message");
    assertEquals(0, permissionQueue.messageCount(),
        "parking a message must not push it back onto the queue the consumer drains");
  }

  @Test
  void deadLetteredMessagesAccumulateInOrderSoTheyCanBeReplayed() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("first", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("second", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.initializeBlockingQueue();

    String first = permissionQueue.waitForMessages().get(1);
    String second = permissionQueue.waitForMessages().get(1);
    permissionQueue.deadLetter(first);
    permissionQueue.deadLetter(second);

    assertEquals(2, permissionQueue.deadLetterCount());
    try (Jedis jedis = new Jedis("127.0.0.1", redis.port())) {
      assertEquals(List.of("first", "second"), jedis.lrange(
              permissionQueue.getDeadLetterQueueName(), 0, -1).stream().map(BlockingQueueServiceTest::readEvent)
          .map(SearchPermissionQueueEvent::getId).toList());
    }
  }

  @Test
  void theDeadLetterQueueIsNamedAfterTheQueueItServes() {
    assertEquals(QueueTestConfig.queueName(QueueService.SEARCH_PERMISSION_QUEUE_ID) + "-dead-letter",
        permissionQueue.getDeadLetterQueueName());
  }

  @Test
  void thereIsNothingToParkForAnAbsentMessage() {
    permissionQueue.initializeBlockingQueue();

    assertFalse(permissionQueue.deadLetter(null));
    assertEquals(0, permissionQueue.deadLetterCount());
  }

  @Test
  void anUnacknowledgedClaimIsRecoveredAheadOfNewerMessages() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("first", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.initializeBlockingQueue();
    assertEquals("first", readEvent(permissionQueue.waitForMessages().get(1)).getId());

    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("second", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.initializeBlockingQueue();

    assertEquals("first", readEvent(permissionQueue.waitForMessages().get(1)).getId());
    assertEquals("second", readEvent(permissionQueue.waitForMessages().get(1)).getId());
  }

  /**
   * An idle consumer must come back rather than block forever, and must come back empty-handed
   * rather than with something it did not claim: the caller treats a non-empty result as a message
   * to handle.
   */
  @Test
  void anEmptyQueueYieldsAnIdleTurn() {
    permissionQueue.initializeBlockingQueue();

    assertEquals(List.of(), permissionQueue.waitForMessages());
    assertEquals(0, permissionQueue.inFlightCount(), "an idle turn claims nothing");
  }

  /**
   * The claim and the recovery are Lua scripts so that they run on every Redis CEDAR deploys to,
   * not only on the 6.2 and later that implement LMOVE. A server below the declared minimum is
   * named by the health check instead of rejecting commands a consumer would retry forever.
   */
  @Test
  void theServerIsVerifiedAgainstTheVersionTheQueuesRequire() {
    permissionQueue.verifyConnectivity();

    assertTrue(new RedisServerVersion(6, 0, 16).isAtLeast(QueueService.MINIMUM_SERVER_VERSION),
        "the queues must run on the oldest server in the estate");
  }

  @Test
  void acknowledgingAClaimRemovesItFromTheProcessingQueue() {
    permissionQueue.enqueueEvent(
        new SearchPermissionQueueEvent("done", SearchPermissionQueueEventType.RESOURCE_MOVED));
    permissionQueue.initializeBlockingQueue();
    String rawMessage = permissionQueue.waitForMessages().get(1);

    assertEquals(1, permissionQueue.inFlightCount());
    assertTrue(permissionQueue.acknowledge(rawMessage));
    assertEquals(0, permissionQueue.inFlightCount());
  }

  private static SearchPermissionQueueEvent readEvent(String json) {
    try {
      return JsonMapper.MAPPER.readValue(json, SearchPermissionQueueEvent.class);
    } catch (Exception e) {
      throw new IllegalStateException("The queued message did not deserialize: " + json, e);
    }
  }
}
