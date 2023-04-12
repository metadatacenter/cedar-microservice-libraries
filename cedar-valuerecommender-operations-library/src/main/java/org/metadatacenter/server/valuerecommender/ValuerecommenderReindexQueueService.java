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
    try (Jedis jedis = pool.getResource()) {
      String json = null;
      try {
        json = JsonMapper.MAPPER.writeValueAsString(message);
      } catch (JsonProcessingException e) {
        log.error("Error while enqueueing valuerecommender message", e);
      }
      jedis.rpush(queueName, json);
    }
  }

}
