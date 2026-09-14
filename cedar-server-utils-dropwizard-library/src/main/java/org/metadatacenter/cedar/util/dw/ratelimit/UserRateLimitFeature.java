package org.metadatacenter.cedar.util.dw.ratelimit;

import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.FeatureContext;
import org.metadatacenter.cedar.util.dw.CedarMicroserviceResource;

/** Every shared resource receives a policy; new routes default by HTTP method. */
public final class UserRateLimitFeature implements DynamicFeature {
  @Context private HttpServletRequest servletRequest;
  private final UserRateLimits limits;
  public UserRateLimitFeature(UserRateLimits limits) { this.limits = limits; }

  @Override public void configure(ResourceInfo resource, FeatureContext context) {
    if (CedarMicroserviceResource.class.isAssignableFrom(resource.getResourceClass())) {
      // The shared instance-injection feature supplies this request-scoped proxy at startup.
      context.register(new AdmissionFilter(limits, servletRequest));
    }
  }

  @Priority(Priorities.AUTHENTICATION + 100)
  public static final class AdmissionFilter implements ContainerRequestFilter {
    private final HttpServletRequest servletRequest;
    private final UserRateLimits limits;
    public AdmissionFilter(UserRateLimits limits, HttpServletRequest servletRequest) {
      this.limits = limits;
      this.servletRequest = servletRequest;
    }
    @Override public void filter(ContainerRequestContext request) {
      limits.prepare(servletRequest, request.getMethod(),
          Boolean.TRUE.equals(request.getProperty(UserRateLimits.VERIFIED_INTERNAL_SERVICE)));
    }
  }
}
