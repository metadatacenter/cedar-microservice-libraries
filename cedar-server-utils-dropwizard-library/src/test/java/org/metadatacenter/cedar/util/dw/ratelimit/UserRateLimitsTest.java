package org.metadatacenter.cedar.util.dw.ratelimit;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.util.dw.CedarExceptionMapper;
import org.metadatacenter.config.RateLimitConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserAuthSource;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UserRateLimitsTest {
  private static HttpServletRequest request() {
    Map<String, Object> attributes = new HashMap<>();
    return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
        new Class<?>[]{HttpServletRequest.class}, (p, m, a) -> switch (m.getName()) {
          case "getAttribute" -> attributes.get(a[0]);
          case "setAttribute" -> { attributes.put((String) a[0], a[1]); yield null; }
          default -> null;
        });
  }
  private static CedarUser user(CedarUserAuthSource source) {
    CedarUser user = new CedarUser(); user.setId("user-1"); user.setAuthSource(source); return user;
  }
  private static class Store implements QuotaStore {
    int calls; List<String> users = new ArrayList<>(); boolean failed;
    @Override public Decision acquire(String id, String policy) {
      calls++; users.add(id);
      if (failed) throw new IllegalStateException("Redis unavailable");
      return new Decision(false, 7, policy);
    }
    @Override public void close() {}
  }
  private UserRateLimits limits(String mode, Store store, MetricRegistry metrics) {
    return new UserRateLimits(new ObjectMapper().convertValue(Map.of("mode", mode), RateLimitConfig.class), store, metrics);
  }

  @Test void observationMeasuresWithoutRejectingAndChargesEachRequestOnce() {
    var store = new Store(); var metrics = new MetricRegistry(); var limits = limits("observe", store, metrics);
    var request = request(); limits.prepare(request, "GET", false);
    UserRateLimits.check(request, user(CedarUserAuthSource.TOKEN));
    UserRateLimits.check(request, user(CedarUserAuthSource.TOKEN));
    assertEquals(1, store.calls);
    assertEquals(1, metrics.meter("cedar.rateLimits.reads.wouldReject").getCount());
  }

  @Test void apiKeysAndTokensUseTheSameUserIdentityAndTheMapperPreserves429() {
    var store = new Store(); var limits = limits("enforce", store, new MetricRegistry());
    for (var source : List.of(CedarUserAuthSource.TOKEN, CedarUserAuthSource.API_KEY)) {
      var request = request(); limits.prepare(request, "POST", false);
      var exception = assertThrows(UserRateLimitException.class, () -> UserRateLimits.check(request, user(source)));
      var response = new CedarExceptionMapper().toResponse(exception);
      assertEquals(429, response.getStatus());
      assertEquals("7", response.getHeaderString("Retry-After"));
      assertEquals("no-store", response.getHeaderString("Cache-Control"));
      assertEquals("writes", ((Map<?, ?>) response.getEntity()).get("policy"));
      assertThrows(UserRateLimitException.class, () -> UserRateLimits.check(request, user(source)));
    }
    assertEquals(List.of("user-1", "user-1"), store.users);
  }

  @Test void offInternalAndUnauthenticatedRequestsDoNotConsumeUserQuota() {
    var store = new Store();
    var disabled = limits("off", store, new MetricRegistry());
    var request = request(); disabled.prepare(request, "GET", false);
    UserRateLimits.check(request, user(CedarUserAuthSource.TOKEN));
    var limits = limits("enforce", store, new MetricRegistry());
    var internal = request(); limits.prepare(internal, "GET", true);
    UserRateLimits.check(internal, user(CedarUserAuthSource.API_KEY));
    var unauthenticated = request(); limits.prepare(unauthenticated, "GET", false);
    UserRateLimits.check(unauthenticated, null);
    assertEquals(0, store.calls);
  }

  @Test void redisFailureUsesPolicyAndObservationAlwaysAllows() {
    for (String mode : List.of("observe", "enforce")) {
      var store = new Store(); store.failed = true;
      var metrics = new MetricRegistry(); var limits = limits(mode, store, metrics);
      var read = request(); limits.prepare(read, "GET", false);
      UserRateLimits.check(read, user(CedarUserAuthSource.TOKEN));
      var write = request(); limits.prepare(write, "PUT", false);
      if (mode.equals("observe")) UserRateLimits.check(write, user(CedarUserAuthSource.TOKEN));
      else {
        var exception = assertThrows(UserRateLimitException.class, () -> UserRateLimits.check(write, user(CedarUserAuthSource.TOKEN)));
        assertEquals(503, new CedarExceptionMapper().toResponse(exception).getStatus());
      }
      assertEquals(1, metrics.meter("cedar.rateLimits.reads.unavailable").getCount());
      assertEquals(1, metrics.meter("cedar.rateLimits.writes.unavailable").getCount());
    }
  }
}
