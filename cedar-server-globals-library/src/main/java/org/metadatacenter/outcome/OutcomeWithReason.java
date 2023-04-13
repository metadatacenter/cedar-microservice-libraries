package org.metadatacenter.outcome;

import org.metadatacenter.error.CedarErrorKey;

public class OutcomeWithReason {

  private final boolean outcome;
  private final CedarErrorKey reason;

  private OutcomeWithReason(boolean outcome, CedarErrorKey reason) {
    this.outcome = outcome;
    this.reason = reason;
  }

  public static OutcomeWithReason positive() {
    return new OutcomeWithReason(true, null);
  }

  public static OutcomeWithReason negative(CedarErrorKey reason) {
    return new OutcomeWithReason(false, reason);
  }

  public CedarErrorKey getReason() {
    return reason;
  }

  public boolean isPositive() {
    return outcome;
  }

  public boolean isNegative() {
    return !outcome;
  }
}
