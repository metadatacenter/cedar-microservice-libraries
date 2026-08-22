package org.metadatacenter.server.logging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.server.queue.util.EmbeddedRedis;
import org.metadatacenter.server.queue.util.QueueService;
import org.metadatacenter.server.queue.util.QueueTestConfig;
import org.metadatacenter.util.json.JsonMapper;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The application log queue against a real Redis.
 * <p>
 * This is the highest-volume queue in the system - one message per HTTP request through every
 * server - so both halves of its behaviour matter: what it enqueues has to survive the round trip
 * intact, and a Redis that is not there has to cost the request nothing.
 */
@Timeout(30)
class AppLoggerQueueServiceTest {

  private static EmbeddedRedis redis;
  private static CacheServerPersistent config;

  private AppLoggerQueueService appLogQueue;

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
    appLogQueue = new AppLoggerQueueService(config);
    try (Jedis jedis = new Jedis("127.0.0.1", redis.port())) {
      jedis.flushAll();
    }
  }

  @AfterEach
  void tearDown() {
    appLogQueue.close();
  }

  private static AppLogMessage message(String globalRequestId) {
    return new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.REQUEST_FILTER,
        AppLogSubType.END, globalRequestId, "local-" + globalRequestId);
  }

  @Test
  void anEnqueuedLogMessageComesBackIntact() throws Exception {
    appLogQueue.enqueueEvent(message("request-1"));

    appLogQueue.initializeBlockingQueue();
    List<String> popped = appLogQueue.waitForMessages();

    assertNotNull(popped);
    assertEquals(QueueTestConfig.queueName(QueueService.APP_LOG_QUEUE_ID), popped.get(0));

    AppLogMessage read = JsonMapper.MAPPER.readValue(popped.get(1), AppLogMessage.class);
    assertEquals("request-1", read.getGlobalRequestId());
    assertEquals("local-request-1", read.getLocalRequestId());
    assertEquals(SystemComponent.SERVER_RESOURCE, read.getSystemComponent());
    assertEquals(AppLogType.REQUEST_FILTER, read.getType());
    assertEquals(AppLogSubType.END, read.getSubType());
    assertNotNull(read.getLogTime(), "the log time should survive the round trip");
  }

  @Test
  void logMessagesQueueInTheOrderTheyWereWritten() throws Exception {
    appLogQueue.enqueueEvent(message("first"));
    appLogQueue.enqueueEvent(message("second"));

    appLogQueue.initializeBlockingQueue();
    assertEquals("first", JsonMapper.MAPPER
        .readValue(appLogQueue.waitForMessages().get(1), AppLogMessage.class).getGlobalRequestId());
    assertEquals("second", JsonMapper.MAPPER
        .readValue(appLogQueue.waitForMessages().get(1), AppLogMessage.class).getGlobalRequestId());
  }

  @Test
  void nothingIsDroppedWhileRedisIsReachable() {
    appLogQueue.enqueueEvent(message("delivered"));

    assertEquals(0, appLogQueue.getDroppedEventCount());
    appLogQueue.initializeBlockingQueue();
    assertEquals(1, appLogQueue.messageCount());
  }

  /**
   * The filter that produces these messages runs on every request. If an unreachable Redis threw
   * from here it would turn a cache outage into a site outage.
   */
  @Test
  void anUnreachableRedisCostsTheRequestNothing() {
    AppLoggerQueueService offline =
        new AppLoggerQueueService(QueueTestConfig.onPort(EmbeddedRedis.freePort()));
    try {
      assertDoesNotThrow(() -> offline.enqueueEvent(message("dropped")));
      assertDoesNotThrow(() -> offline.enqueueEvent(message("dropped-again")));

      assertEquals(2, offline.getDroppedEventCount(), "each dropped log message is counted");
    } finally {
      offline.close();
    }
  }
}
