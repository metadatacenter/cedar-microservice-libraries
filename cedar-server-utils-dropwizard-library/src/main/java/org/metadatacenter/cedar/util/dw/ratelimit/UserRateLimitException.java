package org.metadatacenter.cedar.util.dw.ratelimit;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** A quota response whose retry header and structured body must survive exception mapping. */
public final class UserRateLimitException extends WebApplicationException {
  public UserRateLimitException(int status, String policy, long retryAfterSeconds) {
    super(Response.status(status).type(MediaType.APPLICATION_JSON_TYPE)
        .header("Retry-After", retryAfterSeconds)
        .header("Cache-Control", "no-store")
        .entity(Map.of("status", status == 429 ? "TOO_MANY_REQUESTS" : "SERVICE_UNAVAILABLE",
            "statusCode", status, "errorType", "rateLimit",
            "error", status == 429 ? "rate_limit_exceeded" : "rate_limit_unavailable",
            "message", status == 429 ? "User request allowance exceeded. Retry later."
                : "Request admission is temporarily unavailable. Retry later.",
            "policy", policy, "retryAfterSeconds", retryAfterSeconds)).build());
  }
}
