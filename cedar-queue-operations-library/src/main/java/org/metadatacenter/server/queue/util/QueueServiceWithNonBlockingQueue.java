package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;

public abstract class QueueServiceWithNonBlockingQueue extends QueueService {

  private static final Logger log = LoggerFactory.getLogger(QueueServiceWithNonBlockingQueue.class);
  protected String queueName;
  protected String processingQueueName;

  public QueueServiceWithNonBlockingQueue(CacheServerPersistent cacheConfig, String queueId) {
    super(cacheConfig);
    queueName = cacheConfig.getQueueName(queueId);
    processingQueueName = queueName + PROCESSING_SUFFIX;
  }

  @Override
  public void close() {
    log.info("Closing pool");
    pool.close();
    log.info("Closed");
  }

  // Each poll borrows a connection from the pool instead of holding one for the lifetime of the
  // service: an unreachable queue (Redis) then affects only the poll that hit it, instead of
  // breaking the service permanently - or, when acquired at startup, preventing boot altogether

  public List<String> claimMessages(int maximumCount) {
    if (maximumCount <= 0) {
      throw new IllegalArgumentException("maximumCount must be positive");
    }
    List<String> messages = new ArrayList<>();
    try (Jedis jedis = pool.getResource()) {
      recoverInFlightMessages(jedis);
      while (messages.size() < maximumCount) {
        String message = moveHeadToTail(jedis, queueName, processingQueueName);
        if (message != null) {
          messages.add(message);
        } else {
          break;
        }
      }
    }
    return messages;
  }

  public boolean acknowledge(String rawMessage) {
    if (rawMessage == null) {
      return false;
    }
    try (Jedis jedis = pool.getResource()) {
      return jedis.lrem(processingQueueName, 1, rawMessage) == 1;
    }
  }

  public String getDeadLetterQueueName() {
    return queueName + DEAD_LETTER_SUFFIX;
  }

  public boolean deadLetter(String rawMessage) {
    if (rawMessage == null) {
      return false;
    }
    try (Jedis jedis = pool.getResource()) {
      Object removed = jedis.eval(DEAD_LETTER_SCRIPT,
          List.of(processingQueueName, getDeadLetterQueueName()), List.of(rawMessage));
      return removed instanceof Long && ((Long) removed) == 1L;
    } catch (Exception e) {
      log.error("Could not move an unprocessable message from {} to {}; it remains in-flight for recovery",
          processingQueueName, getDeadLetterQueueName(), e);
      return false;
    }
  }

  public long deadLetterCount() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.llen(getDeadLetterQueueName());
    }
  }

  public long inFlightCount() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.llen(processingQueueName);
    }
  }

  private void recoverInFlightMessages(Jedis jedis) {
    while (moveTailToHead(jedis, processingQueueName, queueName) != null) {
      // Atomic move; see the blocking queue counterpart for ordering rationale.
    }
  }

  @Override
  public long messageCount() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.llen(queueName);
    }
  }

}
