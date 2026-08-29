package org.metadatacenter.server.valuerecommender;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.queue.util.QueueServiceWithNonBlockingQueue;
import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class ValuerecommenderReindexQueueService extends QueueServiceWithNonBlockingQueue {

  private static final Logger log = LoggerFactory.getLogger(ValuerecommenderReindexQueueService.class);

  public ValuerecommenderReindexQueueService(CacheServerPersistent cacheConfig) {
    super(cacheConfig, VALUERECOMMENDER_QUEUE_ID);
  }

  public void enqueueEvent(ValuerecommenderReindexMessage message) {
    // Enqueueing is best-effort: a failure is logged and the event dropped, so an
    // unreachable queue (Redis) can not fail the request that produced the event
    enqueueEventWithResult(message);
  }

  /**
   * Enqueues an event and reports whether Redis accepted it. Durable callers retain their own work
   * item when this returns false; ordinary write paths can continue to use the best-effort wrapper.
   */
  public boolean enqueueEventWithResult(ValuerecommenderReindexMessage message) {
    String json;
    try {
      json = JsonMapper.MAPPER.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      log.error("The valuerecommender message could not be serialized. Dropping it.", e);
      return false;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(queueName, json);
      return true;
    } catch (Exception e) {
      reportDroppedEvent(log, "valuerecommender message", e);
      return false;
    }
  }

}
