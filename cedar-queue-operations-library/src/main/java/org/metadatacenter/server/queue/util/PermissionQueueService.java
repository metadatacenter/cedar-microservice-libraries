package org.metadatacenter.server.queue.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class PermissionQueueService extends QueueServiceWithBlockingQueue {

  private static final Logger log = LoggerFactory.getLogger(PermissionQueueService.class);

  public PermissionQueueService(CacheServerPersistent cacheConfig) {
    super(cacheConfig, SEARCH_PERMISSION_QUEUE_ID);
  }

  public void enqueueEvent(SearchPermissionQueueEvent event) {
    // Enqueueing is best-effort: a failure is logged and the event dropped, so an
    // unreachable queue (Redis) can not fail the request that produced the event
    String json;
    try {
      json = JsonMapper.MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.error("The permission event could not be serialized. Dropping it.", e);
      return;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(queueName, json);
    } catch (Exception e) {
      reportDroppedEvent(log, "permission event", e);
    }
  }
}
