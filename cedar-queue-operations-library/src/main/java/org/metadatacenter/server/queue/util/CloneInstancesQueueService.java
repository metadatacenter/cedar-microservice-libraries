package org.metadatacenter.server.queue.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.resource.CloneInstancesQueueEvent;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class CloneInstancesQueueService extends QueueServiceWithBlockingQueue {

  private static final Logger log = LoggerFactory.getLogger(CloneInstancesQueueService.class);

  public CloneInstancesQueueService(CacheServerPersistent cacheConfig) {
    super(cacheConfig, CLONE_INSTANCES_QUEUE_ID);
  }

  public void enqueueEvent(CloneInstancesQueueEvent event) {
    try (Jedis jedis = pool.getResource()) {
      String json = null;
      try {
        json = JsonMapper.MAPPER.writeValueAsString(event);
      } catch (JsonProcessingException e) {
        log.error("Error while enqueueing event", e);
      }
      jedis.rpush(queueName, json);
    }
  }
}
