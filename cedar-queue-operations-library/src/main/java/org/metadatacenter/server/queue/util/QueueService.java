package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
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

  public QueueService(CacheServerPersistent cacheConfig) {
    this.cacheConfig = cacheConfig;
    pool = new JedisPool(new JedisPoolConfig(), cacheConfig.getConnection().getHost(),
        cacheConfig.getConnection().getPort(), cacheConfig.getConnection().getTimeout());

  }

  public abstract void close();

  public abstract long messageCount();
}
