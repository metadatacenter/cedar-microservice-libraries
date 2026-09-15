package org.metadatacenter.server.search.util;

import org.metadatacenter.config.CacheServerPersistent;
import org.metadatacenter.server.queue.util.RepeatedFailureLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The rebuild's state in Redis, so every process that indexes sees the same answer.
 *
 * <p>The resource server runs the rebuild; the worker applies the permission cascade. Both write to
 * the index, and with process-local state the worker never learned that a rebuild was under way, so
 * every permission update it applied during one was written to the index that promotion deletes.
 * Redis is already a dependency of both and already holds the queues they share, so it is where this
 * belongs.
 *
 * <p><b>Nothing here may throw.</b> These calls sit on the path of an ordinary save. A Redis that
 * cannot be reached must cost mirroring — degrading to the behaviour before mirroring existed — and
 * never a user's write. Every operation therefore swallows its failure and reports it through a
 * {@link RepeatedFailureLogger}, so an outage is one stack trace and a running count rather than one
 * per save.
 *
 * <p><b>The key is read far more often than it changes.</b> {@link #inProgressIndex()} is consulted
 * on every indexing write, and a permission cascade over a large group is hundreds of thousands of
 * them; a round trip each would be a needless load on Redis and on the cascade. The name is therefore
 * cached for {@link #FRESH_FOR}. Staleness is safe in both directions: a rebuild that has just
 * started loses a second or two of mirroring at its very beginning, where the rebuild has barely
 * begun writing and will read those resources from the graph anyway, and a rebuild that has just
 * finished has its index either promoted — where a mirrored write lands in the live index, which is
 * where it belongs — or abandoned, where it lands in an index nothing will read.
 */
public final class RedisIndexRebuildStore implements IndexRebuildStore {

  private static final Logger log = LoggerFactory.getLogger(RedisIndexRebuildStore.class);

  private static final String PREFIX = "CEDAR-INDEX-REBUILD";
  static final String KEY_INDEX = PREFIX + "-index";
  static final String KEY_WRITTEN = PREFIX + "-written";
  static final String KEY_DELETED = PREFIX + "-deleted";
  static final String KEY_JOB = PREFIX + "-job";

  /** How long the in-progress index name is believed without asking Redis again. */
  static final Duration FRESH_FOR = Duration.ofSeconds(2);

  /**
   * How long the rebuild's keys live if nothing cleans them up. A rebuild that is killed outright
   * leaves them behind, and without an expiry a dead rebuild's name would make every later save
   * mirror into an index that no longer exists. Comfortably longer than any rebuild; refreshed on
   * every write, so a running rebuild never reaches it.
   */
  static final int KEY_TTL_SECONDS = (int) Duration.ofHours(48).toSeconds();

  /** The set cap, as {@link InMemoryIndexRebuildStore#MAX_TRACKED}, enforced here with SCARD. */
  static final int MAX_TRACKED = InMemoryIndexRebuildStore.MAX_TRACKED;

  private static final int POOL_MAX_TOTAL = 8;
  private static final Duration POOL_MAX_WAIT = Duration.ofMillis(100);

  private final JedisPool pool;
  private final RepeatedFailureLogger failures = new RepeatedFailureLogger();

  private volatile String cachedIndex;
  private volatile long cachedUntilNanos;

  public RedisIndexRebuildStore(CacheServerPersistent cacheConfig) {
    JedisPoolConfig poolConfig = new JedisPoolConfig();
    poolConfig.setMaxTotal(POOL_MAX_TOTAL);
    poolConfig.setBlockWhenExhausted(true);
    poolConfig.setMaxWait(POOL_MAX_WAIT);
    this.pool = new JedisPool(poolConfig, cacheConfig.getConnection().getHost(),
        cacheConfig.getConnection().getPort(), cacheConfig.getConnection().getTimeout());
  }

  RedisIndexRebuildStore(JedisPool pool) {
    this.pool = pool;
  }

  /** Run one Redis operation, answering with {@code fallback} rather than throwing if it fails. */
  private <T> T withRedis(Function<Jedis, T> operation, T fallback, String what) {
    try (Jedis jedis = pool.getResource()) {
      return operation.apply(jedis);
    } catch (Exception e) {
      failures.report(log, "The index rebuild state could not be reached in Redis (" + what
          + "). Live writes are not being mirrored into a rebuilding index while this lasts.",
          "failures", e);
      return fallback;
    }
  }

  @Override
  public Optional<String> inProgressIndex() {
    long now = System.nanoTime();
    if (now < cachedUntilNanos) {
      return Optional.ofNullable(cachedIndex);
    }
    String value = withRedis(jedis -> jedis.get(KEY_INDEX), null, "reading the index being rebuilt");
    cachedIndex = value;
    cachedUntilNanos = System.nanoTime() + FRESH_FOR.toNanos();
    return Optional.ofNullable(value);
  }

  @Override
  public void begin(String indexName) {
    withRedis(jedis -> {
      jedis.del(KEY_WRITTEN, KEY_DELETED);
      jedis.setex(KEY_INDEX, KEY_TTL_SECONDS, indexName);
      return null;
    }, null, "announcing the index being rebuilt");
    cachedIndex = indexName;
    cachedUntilNanos = System.nanoTime() + FRESH_FOR.toNanos();
  }

  @Override
  public void end(String indexName) {
    if (indexName == null) {
      return;
    }
    withRedis(jedis -> {
      // Only the rebuild that owns the key may clear it, or a failed older rebuild reaching its
      // cleanup would switch off the mirroring a newer one depends on.
      if (indexName.equals(jedis.get(KEY_INDEX))) {
        jedis.del(KEY_INDEX, KEY_WRITTEN, KEY_DELETED);
      }
      return null;
    }, null, "clearing the index being rebuilt");
    if (indexName.equals(cachedIndex)) {
      cachedIndex = null;
    }
    cachedUntilNanos = 0;
  }

  @Override
  public void recordWrite(String cedarId) {
    move(KEY_WRITTEN, KEY_DELETED, cedarId);
  }

  @Override
  public void recordDelete(String cedarId) {
    move(KEY_DELETED, KEY_WRITTEN, cedarId);
  }

  /** Add to one set and remove from the other, so the two never both hold the same resource. */
  private void move(String into, String outOf, String cedarId) {
    if (cedarId == null || inProgressIndex().isEmpty()) {
      return;
    }
    withRedis(jedis -> {
      if (jedis.scard(into) + jedis.scard(outOf) >= MAX_TRACKED) {
        return null;
      }
      jedis.sadd(into, cedarId);
      jedis.srem(outOf, cedarId);
      jedis.expire(into, KEY_TTL_SECONDS);
      jedis.expire(KEY_INDEX, KEY_TTL_SECONDS);
      return null;
    }, null, "recording a live write");
  }

  @Override
  public Set<String> written() {
    return withRedis(jedis -> jedis.smembers(KEY_WRITTEN), Set.of(), "reading live writes");
  }

  @Override
  public Set<String> deleted() {
    return withRedis(jedis -> jedis.smembers(KEY_DELETED), Set.of(), "reading live deletions");
  }

  @Override
  public boolean overflowed() {
    return withRedis(jedis -> jedis.scard(KEY_WRITTEN) + jedis.scard(KEY_DELETED) >= MAX_TRACKED,
        false, "counting live writes");
  }

  @Override
  public void saveJobRecord(String json) {
    withRedis(jedis -> jedis.setex(KEY_JOB, KEY_TTL_SECONDS, json), null, "saving the rebuild record");
  }

  @Override
  public Optional<String> loadJobRecord() {
    return Optional.ofNullable(withRedis(jedis -> jedis.get(KEY_JOB), null, "reading the rebuild record"));
  }

  @Override
  public void clearJobRecord() {
    withRedis(jedis -> jedis.del(KEY_JOB), null, "clearing the rebuild record");
  }
}
