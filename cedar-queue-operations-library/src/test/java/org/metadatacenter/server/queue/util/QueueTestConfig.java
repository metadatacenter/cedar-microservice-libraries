package org.metadatacenter.server.queue.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Builds the cache configuration the queue services take, pointed at a chosen host and port.
 * <p>
 * The configuration beans expose getters only - in production they are populated by deserializing
 * the YAML - so the test builds them the same way, through Jackson with field access, rather than
 * by adding setters that exist for the tests alone. The queue names mirror the ones in
 * cedar-main.yml, so a test enqueues against the keys production uses.
 */
final class QueueTestConfig {

  /** Matches cedar-main.yml, so the tests exercise the real keys rather than invented ones. */
  private static final Map<String, String> QUEUE_NAMES = Map.of(
      QueueService.SEARCH_PERMISSION_QUEUE_ID, "CEDAR-QUEUE-search-permission",
      QueueService.NCBI_SUBMISSION_QUEUE_ID, "CEDAR-QUEUE-ncbi-submission",
      QueueService.APP_LOG_QUEUE_ID, "CEDAR-QUEUE-app-log",
      QueueService.VALUERECOMMENDER_QUEUE_ID, "CEDAR-QUEUE-valuerecommender",
      QueueService.CLONE_INSTANCES_QUEUE_ID, "CEDAR-QUEUE-cloneInstances");

  private static final int TIMEOUT_MILLIS = 2000;

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

  private QueueTestConfig() {
  }

  static org.metadatacenter.config.CacheServerPersistent onPort(int port) {
    return onHostAndPort("127.0.0.1", port);
  }

  static org.metadatacenter.config.CacheServerPersistent onHostAndPort(String host, int port) {
    Map<String, Object> connection = Map.of("host", host, "port", port, "timeout", TIMEOUT_MILLIS);
    Map<String, Object> cache = Map.of("connection", connection, "queueNames", QUEUE_NAMES);
    return MAPPER.convertValue(cache, org.metadatacenter.config.CacheServerPersistent.class);
  }

  static String queueName(String queueId) {
    return QUEUE_NAMES.get(queueId);
  }
}
