package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.concurrent.atomic.AtomicLong;

public abstract class QueueService {

  public static final String SEARCH_PERMISSION_QUEUE_ID = "searchPermission";
  public static final String NCBI_SUBMISSION_QUEUE_ID = "ncbiSubmission";
  public static final String APP_LOG_QUEUE_ID = "appLog";
  public static final String VALUERECOMMENDER_QUEUE_ID = "valuerecommender";
  public static final String CLONE_INSTANCES_QUEUE_ID = "cloneInstances";

  protected final CacheServerPersistent cacheConfig;
  protected JedisPool pool;

  private final AtomicLong droppedEventCount = new AtomicLong();

  public QueueService(CacheServerPersistent cacheConfig) {
    this.cacheConfig = cacheConfig;
    pool = new JedisPool(new JedisPoolConfig(), cacheConfig.getConnection().getHost(),
        cacheConfig.getConnection().getPort(), cacheConfig.getConnection().getTimeout());

  }

  /**
   * Records and logs a dropped event. Enqueueing is best-effort, so a drop does not fail the
   * request that produced the event; this keeps drops visible instead of silent: every drop is
   * logged with a running total per service, so log monitoring can alert on the pattern, and
   * the count is available programmatically.
   */
  protected void reportDroppedEvent(Logger log, String eventNoun, Exception cause) {
    long dropped = droppedEventCount.incrementAndGet();
    log.error("The " + eventNoun + " could not be enqueued. The queue (Redis) may be unreachable. Dropping it. "
        + "(" + dropped + " dropped since startup)", cause);
  }

  public long getDroppedEventCount() {
    return droppedEventCount.get();
  }

  public abstract void close();

  public abstract long messageCount();
}
