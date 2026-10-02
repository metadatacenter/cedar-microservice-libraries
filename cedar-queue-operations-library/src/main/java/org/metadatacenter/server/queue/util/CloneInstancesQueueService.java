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

  /** A recovered execution has an uncertain mutation outcome and must never be started again. */
  public boolean beginExecution(String rawMessage) {
    try (Jedis jedis = pool.getResource()) {
      return jedis.hsetnx(queueName + ":executions", rawMessage, "started") == 1;
    }
  }

  @Override public boolean acknowledge(String rawMessage) {
    if (rawMessage == null) return false;
    try (Jedis jedis = pool.getResource()) {
      // An acknowledgement whose response was lost is still complete. Do not wait forever for a
      // second removal. Identical queued payloads represent the same event, so acknowledge them too.
      Object result = jedis.eval("redis.call('LREM', KEYS[1], 0, ARGV[1]); "
              + "redis.call('LREM', KEYS[2], 0, ARGV[1]); "
              + "redis.call('HDEL', KEYS[3], ARGV[1]); return 1",
          java.util.List.of(processingQueueName, queueName, queueName + ":executions"),
          java.util.List.of(rawMessage));
      return Long.valueOf(1).equals(result);
    }
  }

  @Override public boolean deadLetter(String rawMessage) {
    if (rawMessage == null) return false;
    try (Jedis jedis = pool.getResource()) {
      Object result = jedis.eval("local n = redis.call('LREM', KEYS[1], 0, ARGV[1]); "
              + "if n > 0 then redis.call('LREM', KEYS[4], 0, ARGV[1]); "
              + "redis.call('RPUSH', KEYS[2], ARGV[1]); "
              + "redis.call('HDEL', KEYS[3], ARGV[1]); return 1 end; return 0",
          java.util.List.of(processingQueueName, getDeadLetterQueueName(), queueName + ":executions", queueName),
          java.util.List.of(rawMessage));
      return Long.valueOf(1).equals(result);
    } catch (RuntimeException e) {
      log.error("Could not park an uncertain clone; its execution fence remains for recovery", e);
      return false;
    }
  }

  public void enqueueEvent(CloneInstancesQueueEvent event) {
    // Enqueueing is best-effort: a failure is logged and the event dropped, so an
    // unreachable queue (Redis) can not fail the request that produced the event
    String json;
    try {
      json = JsonMapper.STRICT_MAPPER.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.error("The clone-instances event could not be serialized. Dropping it.", e);
      return;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(queueName, json);
    } catch (Exception e) {
      reportDroppedEvent(log, "clone-instances event", e);
    }
  }
}
