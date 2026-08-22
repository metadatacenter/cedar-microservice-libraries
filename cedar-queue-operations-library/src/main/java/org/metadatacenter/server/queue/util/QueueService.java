package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public abstract class QueueService {

  public static final String SEARCH_PERMISSION_QUEUE_ID = "searchPermission";
  public static final String NCBI_SUBMISSION_QUEUE_ID = "ncbiSubmission";
  public static final String APP_LOG_QUEUE_ID = "appLog";
  public static final String VALUERECOMMENDER_QUEUE_ID = "valuerecommender";
  public static final String CLONE_INSTANCES_QUEUE_ID = "cloneInstances";

  protected final CacheServerPersistent cacheConfig;
  protected JedisPool pool;

  private final RepeatedFailureLogger droppedEventLogger = new RepeatedFailureLogger();

  public QueueService(CacheServerPersistent cacheConfig) {
    this.cacheConfig = cacheConfig;
    pool = new JedisPool(new JedisPoolConfig(), cacheConfig.getConnection().getHost(),
        cacheConfig.getConnection().getPort(), cacheConfig.getConnection().getTimeout());

  }

  /**
   * Records and logs a dropped event. Enqueueing is best-effort, so a drop does not fail the
   * request that produced the event; this keeps drops visible instead of silent: every drop is
   * logged with a running total per service, so log monitoring can alert on the pattern, and
   * the count is available programmatically. Only the first drop carries a stack trace - see
   * {@link RepeatedFailureLogger}.
   */
  protected void reportDroppedEvent(Logger log, String eventNoun, Exception cause) {
    droppedEventLogger.report(log, "The " + eventNoun + " could not be enqueued. "
        + "The queue (Redis) may be unreachable. Dropping it.", "dropped", cause);
  }

  public long getDroppedEventCount() {
    return droppedEventLogger.getCount();
  }

  public abstract void close();

  public abstract long messageCount();
}
