package org.metadatacenter.server.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.queue.util.QueueServiceWithBlockingQueue;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class AppLoggerQueueService extends QueueServiceWithBlockingQueue {

  private static final Logger log = LoggerFactory.getLogger(AppLoggerQueueService.class);

  private final CypherLogFilter cypherLogFilter;

  public AppLoggerQueueService(CacheServerPersistent cacheConfig) {
    this(cacheConfig, new CypherLogFilter());
  }

  AppLoggerQueueService(CacheServerPersistent cacheConfig, CypherLogFilter cypherLogFilter) {
    super(cacheConfig, APP_LOG_QUEUE_ID);
    this.cypherLogFilter = cypherLogFilter;
  }

  public void enqueueEvent(AppLogMessage message)
  {
    // Cypher logging used to be all-or-nothing here, and the "nothing" arm was commented out with a
    // note that the volume was not worth carrying on an ongoing basis. CypherLogFilter replaces that
    // switch: it excludes the named authentication lookups, which are one message in four and the
    // same query every time, and keeps every other Cypher query for the rollups and the outlier
    // tables that read them. See CypherLogFilter for the configuration.
    if (!cypherLogFilter.accepts(message)) {
      return;
    }
    // Enqueueing is best-effort: a failure is logged and the message dropped, so an unreachable
    // queue (Redis) can not fail the request that produced the log message
    String json;
    try {
      json = JsonMapper.STRICT_MAPPER.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      log.error("The log message could not be serialized. Dropping it.", e);
      return;
    }
    try (Jedis jedis = pool.getResource()) {
      jedis.rpush(queueName, json);
    } catch (Exception e) {
      reportDroppedEvent(log, "log message", e);
    }
  }

  /**
   * How many Cypher log messages the filter has excluded. Distinct from the dropped count, which
   * counts messages lost to a failure: these were never meant to be carried.
   */
  public long getSuppressedEventCount() {
    return cypherLogFilter.getSuppressedEventCount();
  }

}
