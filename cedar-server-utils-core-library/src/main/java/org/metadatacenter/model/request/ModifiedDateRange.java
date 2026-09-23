package org.metadatacenter.model.request;

/** Last-modified instants in epoch milliseconds: inclusive lower, exclusive upper bound. */
public record ModifiedDateRange(Long after, Long before) {
  public static final ModifiedDateRange ALL = new ModifiedDateRange(null, null);

  public ModifiedDateRange {
    if (after != null && before != null && after >= before) {
      throw new IllegalArgumentException("modified_before must be later than modified_after");
    }
  }

  public boolean isUnbounded() { return after == null && before == null; }
  public boolean contains(long timestamp) {
    return (after == null || timestamp >= after) && (before == null || timestamp < before);
  }
}
