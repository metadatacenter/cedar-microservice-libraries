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

  public QueueServiceWithNonBlockingQueue(CacheServerPersistent cacheConfig, String queueId) {
    super(cacheConfig);
    queueName = cacheConfig.getQueueName(queueId);
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

  public List<String> getAllMessages() {
    List<String> messages = new ArrayList<>();
    try (Jedis jedis = pool.getResource()) {
      boolean doRead = true;
      while (doRead) {
        String message = jedis.lpop(queueName);
        if (message != null) {
          messages.add(message);
        } else {
          doRead = false;
        }
      }
    }
    return messages;
  }

  @Override
  public long messageCount() {
    try (Jedis jedis = pool.getResource()) {
      return jedis.llen(queueName);
    }
  }

}
