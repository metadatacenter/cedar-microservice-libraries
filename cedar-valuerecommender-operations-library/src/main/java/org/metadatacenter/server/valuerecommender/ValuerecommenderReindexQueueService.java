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
    String json;
    try {
      json = JsonMapper.MAPPER.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      log.error("The valuerecommender message could not be serialized. Dropping it.", e);
      return;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(queueName, json);
    } catch (Exception e) {
      log.error("The valuerecommender message could not be enqueued. The queue (Redis) may be unreachable. Dropping it.", e);
    }
  }

}
