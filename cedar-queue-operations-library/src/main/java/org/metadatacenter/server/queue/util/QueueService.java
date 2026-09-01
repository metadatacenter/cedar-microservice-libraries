package org.metadatacenter.server.queue.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public abstract class QueueService {

  public static final String SEARCH_PERMISSION_QUEUE_ID = "searchPermission";
  public static final String NCBI_SUBMISSION_QUEUE_ID = "ncbiSubmission";
  public static final String APP_LOG_QUEUE_ID = "appLog";
  public static final String VALUERECOMMENDER_QUEUE_ID = "valuerecommender";
  public static final String CLONE_INSTANCES_QUEUE_ID = "cloneInstances";

  protected static final String DEAD_LETTER_SUFFIX = "-dead-letter";
  protected static final String PROCESSING_SUFFIX = "-processing";

  /**
   * The oldest Redis these queues can drive.
   * <p>
   * Every move below is a Lua script, and EVAL has been available since 2.6. The same moves are
   * single commands from 6.2 onwards, as LMOVE and BLMOVE, but CEDAR runs against servers older
   * than that, and a deployment discovers the difference as a rejected command rather than as a
   * failure to start. Raise this only alongside the commands the scripts call.
   */
  public static final RedisServerVersion MINIMUM_SERVER_VERSION = new RedisServerVersion(2, 6, 0);

  /**
   * Moves the message at the head of one list to the tail of another, and returns it.
   * <p>
   * This is how a consumer claims work: the message leaves the queue and joins the processing list
   * in one step, so it is never absent from both, and a consumer that stops before acknowledging
   * leaves it recoverable rather than losing it.
   */
  private static final String HEAD_TO_TAIL_SCRIPT =
      "local message = redis.call('LPOP', KEYS[1]); "
          + "if message then redis.call('RPUSH', KEYS[2], message); end; "
          + "return message";

  /** The reverse move, which returns a claimed message to the queue ahead of newer work. */
  private static final String TAIL_TO_HEAD_SCRIPT =
      "local message = redis.call('RPOP', KEYS[1]); "
          + "if message then redis.call('LPUSH', KEYS[2], message); end; "
          + "return message";

  protected static final String DEAD_LETTER_SCRIPT =
      "local removed = redis.call('LREM', KEYS[1], 1, ARGV[1]); "
          + "if removed == 1 then redis.call('RPUSH', KEYS[2], ARGV[1]); end; return removed";

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

  public void verifyConnectivity() {
    try (var jedis = pool.getResource()) {
      String response = jedis.ping();
      if (!"PONG".equals(response == null ? null : response.toUpperCase(Locale.ROOT))) {
        throw new IllegalStateException("Redis ping returned " + response);
      }
      requireSupportedServer(jedis);
    }
  }

  /**
   * Rejects a server that answers but cannot run what the queues issue.
   * <p>
   * Left to the consumers, that server appears as an unknown-command error inside a retry loop no
   * retry can clear, reported as an outage of a queue that is in fact reachable. Naming it here
   * puts it in the health check every server already registers.
   */
  private void requireSupportedServer(Jedis jedis) {
    Optional<RedisServerVersion> version = RedisServerVersion.parse(jedis.info("server"));
    if (version.isPresent() && !version.get().isAtLeast(MINIMUM_SERVER_VERSION)) {
      throw new IllegalStateException("Redis " + version.get() + " is older than the "
          + MINIMUM_SERVER_VERSION + " these queues require");
    }
  }

  /**
   * Claims the message at the head of {@code source} into the tail of {@code destination}.
   *
   * @return the message, or null when {@code source} is empty
   */
  protected static String moveHeadToTail(Jedis jedis, String source, String destination) {
    return movedMessage(jedis.eval(HEAD_TO_TAIL_SCRIPT, List.of(source, destination), List.of()));
  }

  /**
   * Returns the message at the tail of {@code source} to the head of {@code destination}.
   *
   * @return the message, or null when {@code source} is empty
   */
  protected static String moveTailToHead(Jedis jedis, String source, String destination) {
    return movedMessage(jedis.eval(TAIL_TO_HEAD_SCRIPT, List.of(source, destination), List.of()));
  }

  /** An empty list yields a nil reply, which is the signal that there was nothing to move. */
  private static String movedMessage(Object reply) {
    return reply instanceof String message ? message : null;
  }

  public abstract void close();

  public abstract long messageCount();
}
