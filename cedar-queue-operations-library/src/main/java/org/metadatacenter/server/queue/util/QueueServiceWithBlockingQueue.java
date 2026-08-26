package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.args.ListDirection;

import java.util.List;

public abstract class QueueServiceWithBlockingQueue extends QueueService {

  private static final Logger log = LoggerFactory.getLogger(QueueServiceWithBlockingQueue.class);
  private static final String DEAD_LETTER_SUFFIX = "-dead-letter";
  private static final String PROCESSING_SUFFIX = "-processing";
  private static final String DEAD_LETTER_SCRIPT = "local removed = redis.call('LREM', KEYS[1], 1, ARGV[1]); "
      + "if removed == 1 then redis.call('RPUSH', KEYS[2], ARGV[1]); end; return removed";
  protected Jedis blockingQueue;
  protected String queueName;
  protected String processingQueueName;

  public QueueServiceWithBlockingQueue(CacheServerPersistent cacheConfig, String queueId) {
    super(cacheConfig);
    queueName = cacheConfig.getQueueName(queueId);
    processingQueueName = queueName + PROCESSING_SUFFIX;
  }

  @Override
  public void close() {
    log.info("Blocking queue:" + blockingQueue);
    if (blockingQueue != null) {
      log.info("Closing blocking queue");
      blockingQueue.close();
    }
    log.info("Closing pool");
    pool.close();
    log.info("Closed");
  }

  public void initializeBlockingQueue() {
    // Close a previously held connection first: a consumer re-initializes after a failure, and
    // the broken connection would otherwise leak
    if (blockingQueue != null) {
      try {
        blockingQueue.close();
      } catch (Exception e) {
        // The connection is already broken; nothing to preserve
      }
    }
    blockingQueue = pool.getResource();
    recoverInFlightMessages();
  }

  public void interruptWait() {
    if (blockingQueue != null) {
      blockingQueue.close();
    }
  }

  public List<String> waitForMessages() {
    String message = blockingQueue.blmove(queueName, processingQueueName,
        ListDirection.LEFT, ListDirection.RIGHT, 1);
    return message == null ? List.of() : List.of(queueName, message);
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

  /**
   * Moves a message that could not be processed onto the dead-letter queue.
   * <p>
   * A consumer atomically claims a message into the processing list before handling it. Parking
   * the raw payload here keeps it available for inspection and replay instead of losing it, and
   * keeps the dead-letter depth as the signal that events are not being applied.
   * <p>
   * Uses its own pooled connection: the consumer's own connection is the one parked in BLMOVE.
   *
   * @return whether the message reached the dead-letter queue. On failure the atomic script leaves
   * the message in the processing list, where initialization will recover it for another attempt.
   */
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

  private void recoverInFlightMessages() {
    // A process can stop after claiming a message but before acknowledging it. Restore every such
    // message ahead of newer work, preserving FIFO order, before this consumer begins blocking.
    while (blockingQueue.lmove(processingQueueName, queueName,
        ListDirection.RIGHT, ListDirection.LEFT) != null) {
      // Redis performs the move atomically; no payload is materialized here.
    }
  }

  @Override
  public long messageCount() {
    return blockingQueue.llen(queueName);
  }
}
