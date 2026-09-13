package org.metadatacenter.cedar.util.dw.ratelimit;

import org.apache.commons.codec.digest.DigestUtils;
import org.metadatacenter.config.CacheServerConnection;
import org.metadatacenter.config.RateLimitConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** One atomic admission against the user total and the selected operation bucket. */
public final class RedisUserQuotaStore implements QuotaStore {
  private static final String SCRIPT = loadScript();
  private final RateLimitConfig config;
  private final JedisPool pool;

  public RedisUserQuotaStore(RateLimitConfig config, CacheServerConnection connection) {
    this.config = config;
    JedisPoolConfig options = new JedisPoolConfig();
    options.setMaxTotal(8);
    options.setMaxIdle(8);
    options.setMaxWait(Duration.ofMillis(config.getRedisTimeoutMillis()));
    pool = new JedisPool(options, connection.getHost(), connection.getPort(), config.getRedisTimeoutMillis());
  }

  @Override public Decision acquire(String userId, String policy) {
    RateLimitConfig.Policy operation = policy.equals("reads") ? config.getReads() : config.getWrites();
    RateLimitConfig.Policy total = config.getTotal();
    // A stable user, not a credential. Hash tags also keep the two keys in one Redis cluster slot.
    // Observation and enforcement do not consume each other's budgets during a rolling rollout.
    String key = config.getRedisKeyPrefix() + ":{" + DigestUtils.sha256Hex(userId) + "}:" + config.getMode();
    List<String> keys = List.of(key + ":total", key + ":" + policy);
    List<String> args = List.of(Integer.toString(total.getRequestsPerMinute()), Integer.toString(total.getBurst()),
        Integer.toString(operation.getRequestsPerMinute()), Integer.toString(operation.getBurst()));
    try (Jedis jedis = pool.getResource()) {
      List<?> result = (List<?>) jedis.eval(SCRIPT, keys, args);
      return new Decision(((Number) result.get(0)).longValue() == 1,
          ((Number) result.get(1)).longValue(), ((Number) result.get(2)).longValue() == 1 ? "total" : policy);
    }
  }

  @Override public void close() { pool.close(); }

  private static String loadScript() {
    try (var input = RedisUserQuotaStore.class.getResourceAsStream("/cedar-user-quota.lua")) {
      if (input == null) throw new IllegalStateException("Missing cedar-user-quota.lua");
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read cedar-user-quota.lua", e);
    }
  }
}
