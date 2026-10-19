package org.metadatacenter.server.queue.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.resource.CloneInstancesQueueEvent;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CloneInstancesQueueService extends QueueServiceWithBlockingQueue {

  private static final Logger log = LoggerFactory.getLogger(CloneInstancesQueueService.class);

  // Payloads remain wire-compatible. Receipts live separately and change on every claim, including
  // recovery. A stale callback must never operate on a later claim of the identical payload.
  private final ConcurrentMap<String, String> receipts = new ConcurrentHashMap<>();

  private static final String CLAIM = TRANSFER_HELPERS + """
      requireType(KEYS[1], 'list'); requireType(KEYS[2], 'list'); requireType(KEYS[3], 'hash')
      local message = redis.call('LINDEX', KEYS[1], 0)
      if message then
        redis.call('HSET', KEYS[3], message, ARGV[1])
        redis.call('RPUSH', KEYS[2], message)
        redis.call('LPOP', KEYS[1])
      end
      return message
      """;
  private static final String BEGIN = TRANSFER_HELPERS + """
      requireType(KEYS[1], 'hash'); requireType(KEYS[2], 'hash')
      if redis.call('HGET', KEYS[1], ARGV[1]) ~= ARGV[2] then return 0 end
      return redis.call('HSETNX', KEYS[2], ARGV[1], 'started')
      """;
  private static final String ACKNOWLEDGE = TRANSFER_HELPERS + """
      requireType(KEYS[1], 'list'); requireType(KEYS[2], 'list')
      requireType(KEYS[3], 'hash'); requireType(KEYS[4], 'hash')
      if redis.call('HGET', KEYS[4], ARGV[1]) ~= ARGV[2] then return 1 end
      redis.call('LREM', KEYS[1], 0, ARGV[1])
      redis.call('LREM', KEYS[2], 0, ARGV[1])
      redis.call('HDEL', KEYS[3], ARGV[1])
      redis.call('HDEL', KEYS[4], ARGV[1])
      return 1
      """;
  private static final String PARK = TRANSFER_HELPERS + """
      requireType(KEYS[1], 'list'); requireType(KEYS[2], 'list'); requireType(KEYS[4], 'list')
      requireType(KEYS[3], 'hash'); requireType(KEYS[5], 'hash')
      if redis.call('HGET', KEYS[5], ARGV[1]) ~= ARGV[2] then return 0 end
      if not contains(KEYS[1], ARGV[1]) then return 0 end
      redis.call('RPUSH', KEYS[2], ARGV[1])
      redis.call('LREM', KEYS[1], 0, ARGV[1])
      redis.call('LREM', KEYS[4], 0, ARGV[1])
      redis.call('HDEL', KEYS[3], ARGV[1])
      redis.call('HDEL', KEYS[5], ARGV[1])
      return 1
      """;

  public CloneInstancesQueueService(CacheServerPersistent cacheConfig) {
    super(cacheConfig, CLONE_INSTANCES_QUEUE_ID);
  }

  @Override
  public void initializeBlockingQueue() {
    // Failed acknowledgements are retried by the processor before reinitialization. A new claim
    // after recovery gets a new receipt, even when this service object is reused after an outage.
    receipts.clear();
    super.initializeBlockingQueue();
  }

  @Override
  protected String claimOne(Jedis jedis) {
    String receipt = UUID.randomUUID().toString();
    Object reply = jedis.eval(CLAIM, List.of(queueName, processingQueueName, queueName + ":claims"),
        List.of(receipt));
    if (reply instanceof String message) {
      receipts.put(message, receipt);
      return message;
    }
    return null;
  }

  /** A recovered execution has an uncertain mutation outcome and must never be started again. */
  public boolean beginExecution(String rawMessage) {
    String receipt = rawMessage == null ? null : receipts.get(rawMessage);
    if (receipt == null) return false;
    try (Jedis jedis = pool.getResource()) {
      return Long.valueOf(1).equals(jedis.eval(BEGIN,
          List.of(queueName + ":claims", queueName + ":executions"), List.of(rawMessage, receipt)));
    }
  }

  @Override public boolean acknowledge(String rawMessage) {
    if (rawMessage == null) return false;
    String receipt = receipts.get(rawMessage);
    if (receipt == null) return true; // A repeated acknowledgement is a harmless no-op.
    try (Jedis jedis = pool.getResource()) {
      // A lost reply can be retried. A superseded claimant also finishes without removing the
      // replacement's message or execution fence, so the worker does not spin on a stale ack.
      Object result = jedis.eval(ACKNOWLEDGE,
          List.of(processingQueueName, queueName, queueName + ":executions", queueName + ":claims"),
          List.of(rawMessage, receipt));
      boolean acknowledged = Long.valueOf(1).equals(result);
      if (acknowledged) receipts.remove(rawMessage, receipt);
      return acknowledged;
    }
  }

  @Override public boolean deadLetter(String rawMessage) {
    if (rawMessage == null) return false;
    String receipt = receipts.get(rawMessage);
    if (receipt == null) return false;
    try (Jedis jedis = pool.getResource()) {
      Object result = jedis.eval(PARK,
          List.of(processingQueueName, getDeadLetterQueueName(), queueName + ":executions", queueName,
              queueName + ":claims"), List.of(rawMessage, receipt));
      receipts.remove(rawMessage, receipt);
      return Long.valueOf(1).equals(result);
    } catch (RuntimeException e) {
      log.error("Could not park an uncertain clone; its claim and execution fence remain for recovery", e);
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
