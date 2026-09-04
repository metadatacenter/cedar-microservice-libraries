package org.metadatacenter.server.logging.agg;

/**
 * Fixed log-scale latency histogram shared by every rollup table.
 * <p>
 * Durations are logged in nanoseconds; the buckets are defined by ms ceilings. There are
 * {@link #BUCKETS} buckets: bucket i counts durations {@code < CEILINGS_MS[i]} that were not counted
 * by an earlier bucket, and the final bucket is the open {@code >= last ceiling} bin.
 * <p>
 * The whole point of this layout is that histograms <b>merge by element-wise addition</b>: the
 * histogram of any set of rows (an hour, a day in some timezone, a whole date range) is the sum of the
 * per-row-group histograms, so p50/p95/p99 over an arbitrary range are recoverable at query time
 * without keeping the raw durations. Percentiles are therefore approximate to bucket width — which is
 * all a latency percentile ever is.
 */
public final class LatencyHistogram {

  private LatencyHistogram() {
  }

  /** Bucket ceilings in milliseconds. {@code CEILINGS_MS.length + 1 == BUCKETS}. */
  public static final int[] CEILINGS_MS = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000, 30000};

  /** Number of histogram buckets (h0..h14). */
  public static final int BUCKETS = CEILINGS_MS.length + 1;

  private static final long NANOS_PER_MS = 1_000_000L;

  /**
   * @param durationNanos a logged duration in nanoseconds
   * @return the histogram bucket index in {@code [0, BUCKETS)}
   */
  public static int bucketForNanos(long durationNanos) {
    long ms = durationNanos / NANOS_PER_MS;
    for (int i = 0; i < CEILINGS_MS.length; i++) {
      if (ms < CEILINGS_MS[i]) {
        return i;
      }
    }
    return BUCKETS - 1;
  }

  /** Add {@code src} into {@code dst} in place (used when folding rows and when merging rollups). */
  public static void addInto(int[] dst, int[] src) {
    for (int i = 0; i < BUCKETS; i++) {
      dst[i] += src[i];
    }
  }

  /** Sum of all buckets. */
  public static long total(long[] hist) {
    long t = 0;
    for (long v : hist) {
      t += v;
    }
    return t;
  }

  /**
   * Approximate percentile (in nanos) from a merged histogram, interpolating in log space within the
   * crossing bucket. This is what lets p50/p95/p99 over any date range be recovered by first SUMming
   * the per-hour bucket columns and then calling this. Returns 0 for an empty histogram.
   *
   * @param p in [0,1], e.g. 0.95
   */
  public static long percentileNanos(long[] hist, double p) {
    long total = total(hist);
    if (total == 0) {
      return 0;
    }
    double target = p * total;
    long cum = 0;
    for (int i = 0; i < BUCKETS; i++) {
      if (cum + hist[i] >= target) {
        double loMs = i == 0 ? 0.5 : CEILINGS_MS[i - 1];
        double hiMs = i == BUCKETS - 1 ? CEILINGS_MS[CEILINGS_MS.length - 1] * 2.0 : CEILINGS_MS[i];
        double frac = hist[i] == 0 ? 0 : (target - cum) / hist[i];
        double lnLo = Math.log(Math.max(loMs, 0.5));
        double lnHi = Math.log(hiMs);
        double ms = Math.exp(lnLo + (lnHi - lnLo) * frac);
        return (long) (ms * 1_000_000L);
      }
      cum += hist[i];
    }
    return (long) (CEILINGS_MS[CEILINGS_MS.length - 1] * 2.0 * 1_000_000L);
  }

  /** A human label per bucket, for UI / debugging. */
  public static String label(int bucket) {
    if (bucket == 0) {
      return "<" + CEILINGS_MS[0] + "ms";
    }
    if (bucket == BUCKETS - 1) {
      return ">=" + CEILINGS_MS[CEILINGS_MS.length - 1] + "ms";
    }
    return CEILINGS_MS[bucket - 1] + "-" + CEILINGS_MS[bucket] + "ms";
  }
}
