package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.List;

public abstract class QueueServiceWithBlockingQueue extends QueueService {

  private static final Logger log = LoggerFactory.getLogger(QueueServiceWithBlockingQueue.class);
  private static final String DEAD_LETTER_SUFFIX = "-dead-letter";
  protected Jedis blockingQueue;
  protected String queueName;

  public QueueServiceWithBlockingQueue(CacheServerPersistent cacheConfig, String queueId) {
    super(cacheConfig);
    queueName = cacheConfig.getQueueName(queueId);
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
  }

  public List<String> waitForMessages() {
    return blockingQueue.blpop(0, queueName);
  }

  public String getDeadLetterQueueName() {
    return queueName + DEAD_LETTER_SUFFIX;
  }

  /**
   * Moves a message that could not be processed onto the dead-letter queue.
   * <p>
   * A consumer takes a message off the queue before processing it, so a message the consumer
   * cannot handle is already gone from the queue by the time it fails. Parking the raw payload
   * here keeps it available for inspection and replay instead of losing it, and keeps the
   * dead-letter depth as the signal that events are not being applied.
   * <p>
   * Uses its own pooled connection: the consumer's own connection is the one parked in BLPOP.
   *
   * @return whether the message reached the dead-letter queue. A false means the message is lost,
   * which is why it is reported rather than returned silently.
   */
  public boolean deadLetter(String rawMessage) {
    if (rawMessage == null) {
      return false;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(getDeadLetterQueueName(), rawMessage);
      return true;
    } catch (Exception e) {
      reportDroppedEvent(log, "unprocessable message bound for " + getDeadLetterQueueName(), e);
      return false;
    }
  }

  public long deadLetterCount() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.llen(getDeadLetterQueueName());
    }
  }

  @Override
  public long messageCount() {
    return blockingQueue.llen(queueName);
  }
}
