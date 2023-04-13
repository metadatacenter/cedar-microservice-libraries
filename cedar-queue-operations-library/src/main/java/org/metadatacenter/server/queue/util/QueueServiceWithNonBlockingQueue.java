package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;

public abstract class QueueServiceWithNonBlockingQueue extends QueueService {

  private static final Logger log = LoggerFactory.getLogger(QueueServiceWithNonBlockingQueue.class);
  protected Jedis nonBlockingQueue;
  protected String queueName;

  public QueueServiceWithNonBlockingQueue(CacheServerPersistent cacheConfig, String queueId) {
    super(cacheConfig);
    queueName = cacheConfig.getQueueName(queueId);
  }

  @Override
  public void close() {
    log.info("NonBlocking queue:" + nonBlockingQueue);
    if (nonBlockingQueue != null) {
      log.info("Closing nonBlocking queue");
      nonBlockingQueue.close();
    }
    log.info("Closing pool");
    pool.close();
    log.info("Closed");
  }

  public void initializeNonBlockingQueue() {
    nonBlockingQueue = pool.getResource();
  }

  public List<String> getAllMessages() {
    List<String> messages = new ArrayList<>();
    boolean doRead = true;
    while (doRead) {
      String message = nonBlockingQueue.lpop(queueName);
      if (message != null) {
        messages.add(message);
      } else {
        doRead = false;
      }
    }
    return messages;
  }

  @Override
  public long messageCount() {
    return nonBlockingQueue.llen(queueName);
  }

}
