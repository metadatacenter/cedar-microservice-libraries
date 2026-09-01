package org.metadatacenter.server.valuerecommender;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.server.queue.util.EmbeddedRedis;
import org.metadatacenter.server.queue.util.QueueTestConfig;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageActionType;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessageResourceType;
import org.metadatacenter.util.json.JsonMapper;
import redis.clients.jedis.Jedis;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The value-recommender reindex queue against a real Redis. Unlike the other queue services this
 * one is drained in batches by a polling consumer rather than a blocking pop, so what matters here
 * is that a whole batch comes back in one poll, in order, and that the queue is left empty.
 */
@Timeout(30)
class ValuerecommenderReindexQueueServiceTest {

  private static final String TEMPLATE_IRI = "https://repo.metadatacenter.orgx/templates/template-1";
  private static final String INSTANCE_IRI = "https://repo.metadatacenter.orgx/template-instances/instance-1";

  private static EmbeddedRedis redis;
  private static CacheServerPersistent config;

  private ValuerecommenderReindexQueueService reindexQueue;

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
    reindexQueue = new ValuerecommenderReindexQueueService(config);
    try (Jedis jedis = new Jedis("127.0.0.1", redis.port())) {
      jedis.flushAll();
    }
  }

  @AfterEach
  void tearDown() {
    reindexQueue.close();
  }

  private static ValuerecommenderReindexMessage message(ValuerecommenderReindexMessageActionType action) {
    return new ValuerecommenderReindexMessage(
        CedarTemplateId.build(TEMPLATE_IRI),
        CedarTemplateInstanceId.build(INSTANCE_IRI),
        ValuerecommenderReindexMessageResourceType.INSTANCE,
        action);
  }

  @Test
  void anEnqueuedMessageComesBackFromThePoll() throws Exception {
    reindexQueue.enqueueEvent(message(ValuerecommenderReindexMessageActionType.CREATED));

    List<String> drained = reindexQueue.claimMessages(100);

    assertEquals(1, drained.size());
    ValuerecommenderReindexMessage read =
        JsonMapper.MAPPER.readValue(drained.get(0), ValuerecommenderReindexMessage.class);
    assertEquals(TEMPLATE_IRI, read.getTemplateId().getId());
    assertEquals(ValuerecommenderReindexMessageResourceType.INSTANCE, read.getResourceType());
    assertEquals(ValuerecommenderReindexMessageActionType.CREATED, read.getActionType());
  }

  @Test
  void onePollDrainsTheWholeBatchInOrder() throws Exception {
    reindexQueue.enqueueEvent(message(ValuerecommenderReindexMessageActionType.CREATED));
    reindexQueue.enqueueEvent(message(ValuerecommenderReindexMessageActionType.UPDATED));
    reindexQueue.enqueueEvent(message(ValuerecommenderReindexMessageActionType.DELETED));

    List<String> drained = reindexQueue.claimMessages(100);

    assertEquals(3, drained.size(), "the consumer reads a whole batch per poll");
    assertEquals(List.of(
            ValuerecommenderReindexMessageActionType.CREATED,
            ValuerecommenderReindexMessageActionType.UPDATED,
            ValuerecommenderReindexMessageActionType.DELETED),
        drained.stream().map(ValuerecommenderReindexQueueServiceTest::readAction).toList());
  }

  @Test
  void anAcknowledgedBatchIsLeftEmpty() {
    reindexQueue.enqueueEvent(message(ValuerecommenderReindexMessageActionType.CREATED));

    List<String> claimed = reindexQueue.claimMessages(100);
    assertEquals(1, claimed.size());
    claimed.forEach(reindexQueue::acknowledge);
    assertEquals(0, reindexQueue.messageCount());
    assertEquals(List.of(), reindexQueue.claimMessages(100), "the next poll finds nothing left");
  }

  @Test
  void nothingIsDroppedWhileRedisIsReachable() {
    assertTrue(reindexQueue.enqueueEventWithResult(
        message(ValuerecommenderReindexMessageActionType.UPDATED)));

    assertEquals(0, reindexQueue.getDroppedEventCount());
    assertEquals(1, reindexQueue.messageCount());
  }

  @Test
  void anUnreachableRedisDropsAndCountsWithoutThrowing() {
    ValuerecommenderReindexQueueService offline =
        new ValuerecommenderReindexQueueService(QueueTestConfig.onPort(EmbeddedRedis.freePort()));
    try {
      assertDoesNotThrow(() -> offline.enqueueEvent(message(ValuerecommenderReindexMessageActionType.CREATED)));
      assertFalse(offline.enqueueEventWithResult(message(ValuerecommenderReindexMessageActionType.UPDATED)));

      assertEquals(2, offline.getDroppedEventCount());
    } finally {
      offline.close();
    }
  }

  private static ValuerecommenderReindexMessageActionType readAction(String json) {
    try {
      return JsonMapper.MAPPER.readValue(json, ValuerecommenderReindexMessage.class).getActionType();
    } catch (Exception e) {
      throw new IllegalStateException("The queued message did not deserialize: " + json, e);
    }
  }
}
