package org.metadatacenter.server.logging.agg;

/**
 * Read-side DTOs for the Live Log Explorer — row-level detail over the RAW log tables (last ≤30 days,
 * before the prune), the forensic complement to the aggregated {@link AggQueryResults}. Serialized
 * straight to JSON by the monitor-server. Durations are nanos.
 */
public final class LogExplorerResults {

  private LogExplorerResults() {
  }

  public record RequestRow(String globalRequestId, String requestTime, String component,
                           String httpMethod, String path, String handler, String userId,
                           String authSource, String apiKeyHash, Integer status, long durationNanos,
                           String errorPack) {
  }

  public record CypherRow(String logTime, String component, String operation, String runnableHash,
                          long durationNanos, String runnable, String parameters, String handler) {
  }
}
