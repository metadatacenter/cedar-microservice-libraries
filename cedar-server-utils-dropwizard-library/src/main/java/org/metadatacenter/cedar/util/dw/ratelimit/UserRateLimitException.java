package org.metadatacenter.cedar.util.dw.ratelimit;

import jakarta.ws.rs.WebApplicationException;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.util.http.CedarResponse;

/** A quota rejection uses the native error contract, including retry metadata. */
public final class UserRateLimitException extends WebApplicationException {
  public UserRateLimitException(int status, String policy, long retryAfterSeconds) {
    super(CedarResponse.status(status)
        .header("Retry-After", retryAfterSeconds)
        .header("Cache-Control", "no-store")
        .errorKey(status == 429 ? CedarErrorKey.RATE_LIMIT_EXCEEDED : CedarErrorKey.RATE_LIMIT_UNAVAILABLE)
        .message(status == 429 ? "User request allowance exceeded. Retry later."
            : "Request admission is temporarily unavailable. Retry later.")
        .parameter("policy", policy)
        .parameter("retryAfterSeconds", retryAfterSeconds).build());
  }
}
