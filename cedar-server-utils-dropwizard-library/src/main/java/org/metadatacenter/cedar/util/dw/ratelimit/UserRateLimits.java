package org.metadatacenter.cedar.util.dw.ratelimit;

import com.codahale.metrics.MetricRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.metadatacenter.config.RateLimitConfig;
import org.metadatacenter.server.security.model.user.CedarUser;

/** Per-application service; per-request state is installed by the Jersey filter, never a header. */
public final class UserRateLimits {
  public static final String VERIFIED_INTERNAL_SERVICE = UserRateLimits.class.getName() + ".internal";
  private static final String ADMISSION = UserRateLimits.class.getName() + ".admission";
  private final RateLimitConfig config;
  private final QuotaStore store;
  private final MetricRegistry metrics;

  public UserRateLimits(RateLimitConfig config, QuotaStore store, MetricRegistry metrics) {
    this.config = config;
    this.store = store;
    this.metrics = metrics;
    for (String policy : new String[]{"total", "reads", "writes"}) {
      for (String outcome : new String[]{"allowed", "wouldReject", "rejected", "unavailable", "failedOpen", "failedClosed"}) {
        metrics.meter("cedar.rateLimits." + policy + "." + outcome);
      }
    }
  }

  public void prepare(HttpServletRequest request, String method, boolean internal) {
    if (internal) {
      metrics.meter("cedar.rateLimits.internal").mark();
      return;
    }
    if (!config.getMode().equals("off") && request.getAttribute(ADMISSION) == null) {
      String policy = switch (method) { case "GET", "HEAD", "OPTIONS" -> "reads"; default -> "writes"; };
      request.setAttribute(ADMISSION, new Admission(policy));
    }
  }

  /** Called only after the existing authentication succeeded; anonymous handlers never call it. */
  public static void check(HttpServletRequest request, CedarUser user) {
    if (request != null && user != null && request.getAttribute(ADMISSION) instanceof Admission admission) {
      admission.check(user.getId());
    }
  }

  private void mark(String policy, String outcome) {
    metrics.meter("cedar.rateLimits." + policy + "." + outcome).mark();
  }

  private final class Admission {
    private final String policy;
    private boolean checked;
    private UserRateLimitException rejection;
    private Admission(String policy) { this.policy = policy; }
    private synchronized void check(String userId) {
      if (checked) {
        if (rejection != null) throw rejection;
        return;
      }
      checked = true;
      QuotaStore.Decision decision;
      try {
        decision = store.acquire(userId, policy);
      } catch (RuntimeException e) {
        mark(policy, "unavailable");
        RateLimitConfig.Policy operation = policy.equals("reads") ? config.getReads() : config.getWrites();
        boolean closed = operation.getFailureMode().equals("closed") || config.getTotal().getFailureMode().equals("closed");
        if (config.getMode().equals("enforce") && closed) {
          mark(policy, "failedClosed");
          rejection = new UserRateLimitException(503, policy, 1);
          throw rejection;
        }
        mark(policy, "failedOpen");
        return;
      }
      if (decision.allowed()) {
        mark(policy, "allowed");
        mark("total", "allowed");
      } else if (config.getMode().equals("observe")) {
        mark(decision.policy(), "wouldReject");
      } else {
        mark(decision.policy(), "rejected");
        rejection = new UserRateLimitException(429, decision.policy(), decision.retryAfterSeconds());
        throw rejection;
      }
    }
  }
}
