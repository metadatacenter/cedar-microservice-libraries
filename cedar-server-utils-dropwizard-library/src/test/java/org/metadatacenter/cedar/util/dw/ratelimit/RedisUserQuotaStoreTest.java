package org.metadatacenter.cedar.util.dw.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CacheServerConnection;
import org.metadatacenter.config.RateLimitConfig;
import org.metadatacenter.server.queue.util.EmbeddedRedis;
import redis.clients.jedis.Jedis;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class RedisUserQuotaStoreTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private RateLimitConfig config(int total, int reads, int writes) {
    return mapper.convertValue(Map.of("mode", "enforce", "redisTimeoutMillis", 1000,
        "total", Map.of("requestsPerMinute", 1, "burst", total),
        "reads", Map.of("requestsPerMinute", 1, "burst", reads),
        "writes", Map.of("requestsPerMinute", 1, "burst", writes)), RateLimitConfig.class);
  }
  private RedisUserQuotaStore store(RateLimitConfig config, int port) {
    return new RedisUserQuotaStore(config, mapper.convertValue(
        Map.of("host", "127.0.0.1", "port", port, "timeout", 1000), CacheServerConnection.class));
  }

  @Test void separateInstancesShareUserBudgetsAndRejectAtomically() throws Exception {
    try (var redis = EmbeddedRedis.start(); var first = store(config(10, 10, 10), redis.port());
         var second = store(config(10, 10, 10), redis.port())) {
      ExecutorService executor = Executors.newFixedThreadPool(8);
      try {
        var requests = new ArrayList<Future<QuotaStore.Decision>>();
        for (int i = 0; i < 40; i++) {
          var service = i % 2 == 0 ? first : second;
          requests.add(executor.submit(() -> service.acquire("same-user", "reads")));
        }
        int allowed = 0;
        for (var request : requests) if (request.get().allowed()) allowed++;
        assertEquals(10, allowed);
        assertTrue(first.acquire("different-user", "reads").allowed());
      } finally { executor.shutdownNow(); }
    }
  }

  @Test void rejectedOperationDoesNotSpendTheTotalAndTotalCoversBothPolicies() {
    try (var redis = EmbeddedRedis.start(); var limiter = store(config(3, 1, 5), redis.port())) {
      assertTrue(limiter.acquire("user", "reads").allowed());
      var rejection = limiter.acquire("user", "reads");
      assertFalse(rejection.allowed());
      assertEquals("reads", rejection.policy());
      assertTrue(rejection.retryAfterSeconds() > 0);
      assertTrue(limiter.acquire("user", "writes").allowed());
      assertTrue(limiter.acquire("user", "writes").allowed());
      assertEquals("total", limiter.acquire("user", "writes").policy());
      assertFalse(limiter.acquire("user", "writes").allowed());
    }
  }

  @Test void bucketsRefillAndExpireWithoutStoringUserIdentifiers() throws Exception {
    var fast = mapper.convertValue(Map.of("mode", "enforce",
        "total", Map.of("requestsPerMinute", 60000, "burst", 1),
        "reads", Map.of("requestsPerMinute", 60000, "burst", 1)), RateLimitConfig.class);
    try (var redis = EmbeddedRedis.start(); var limiter = store(fast, redis.port());
         var client = new Jedis("127.0.0.1", redis.port())) {
      assertTrue(limiter.acquire("private-user-id", "reads").allowed());
      var keys = client.keys("CEDAR-RATE-LIMIT:*");
      assertEquals(2, keys.size());
      for (String key : keys) {
        assertFalse(key.contains("private-user-id"));
        assertTrue(client.ttl(key) > 0);
      }
      Thread.sleep(20);
      assertTrue(limiter.acquire("private-user-id", "reads").allowed());
      Thread.sleep(2200);
      for (String key : keys) assertFalse(client.exists(key));
    }
  }

  @Test void observationDoesNotConsumeEnforcementAllowanceAndNeverTouchesQueues() {
    try (var redis = EmbeddedRedis.start(); var client = new Jedis("127.0.0.1", redis.port())) {
      client.rpush("CEDAR-QUEUE-app-log", "keep");
      RateLimitConfig observe = mapper.convertValue(Map.of("mode", "observe"), RateLimitConfig.class);
      try (var dry = store(observe, redis.port()); var enforcing = store(config(1, 1, 1), redis.port())) {
        for (int i = 0; i < 50; i++) dry.acquire("user", "reads");
        assertTrue(enforcing.acquire("user", "reads").allowed());
        assertEquals("keep", client.lindex("CEDAR-QUEUE-app-log", 0));
      }
    }
  }
}
