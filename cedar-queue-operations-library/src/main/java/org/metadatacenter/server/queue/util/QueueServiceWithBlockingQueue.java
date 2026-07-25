package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.List;

public abstract class QueueServiceWithBlockingQueue extends QueueService {

  private static final Logger log = LoggerFactory.getLogger(QueueServiceWithBlockingQueue.class);
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

  @Override
  public long messageCount() {
    return blockingQueue.llen(queueName);
  }
}
