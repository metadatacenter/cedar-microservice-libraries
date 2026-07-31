package org.metadatacenter.server.logging.agg;

import java.util.List;

/**
 * Read-side DTOs returned by {@link org.metadatacenter.server.logging.dao.agg.AggregationQueryDAO}
 * and serialized straight to JSON by the monitor-server resources. Durations are nanos (the UI divides).
 */
public final class AggQueryResults {

  private AggQueryResults() {
  }

  public record EndpointStat(String component, String className, String methodName, String httpMethod,
                             long reqCount, long errorCount, long p50Nanos, long p95Nanos,
                             long p99Nanos, long maxNanos) {
  }

  public record CypherStat(String operation, String runnableHash, String sample, long execCount,
                           long p50Nanos, long p95Nanos, long p99Nanos, long maxNanos) {
  }

  public record UserStat(String userId, String authSource, String apiKeyHash, long reqCount,
                         long errorCount) {
  }

  public record TimeBucket(String hourUtc, long reqCount, long errorCount) {
  }

  public record UsageTotals(long reqCount, long errorCount, long p50Nanos, long p95Nanos, long p99Nanos) {
  }

  /** The bundle powering the Insights strip (pattern detection), all for one time range. */
  public record Insights(List<CypherStat> slowestCypher, List<EndpointStat> slowestEndpoints,
                         List<UserStat> heaviestUsers, List<EndpointStat> errorHotspots) {
  }
}
