package org.metadatacenter.server.logging.query;

import java.util.List;
import java.util.Map;

/**
 * Read-side DTOs for the structured query engine, serialized straight to JSON by the monitor-server
 * (same convention as {@code LogExplorerResults} / {@code AggQueryResults}).
 * <p>
 * Every result carries its own provenance — {@code exact}, {@code truncated}, {@code elapsedMs},
 * {@code notes} — so the UI can state precision and caps instead of implying completeness it does not
 * have.
 */
public final class LogQueryResults {

  private LogQueryResults() {
  }

  /** Column type as far as the UI is concerned: how to align, format and copy a cell. */
  public enum ColumnType {STRING, NUMBER, NANOS, TIMESTAMP, TEXT}

  public record ColumnMeta(String key, String label, ColumnType type, String note) {
  }

  /**
   * @param rows       ordered maps keyed by {@link ColumnMeta#key()}
   * @param nextCursor keyset cursor for the following page ("&lt;iso&gt;,&lt;id&gt;"), null when exhausted
   *                   or when grouped
   * @param truncated  the limit was hit — there is more data behind this result
   * @param exact      false when any percentile came from an approximate source (the rollup histograms)
   */
  public record QueryResult(List<ColumnMeta> columns,
                            List<Map<String, Object>> rows,
                            int rowCount,
                            boolean truncated,
                            String nextCursor,
                            long elapsedMs,
                            boolean exact,
                            String source,
                            List<String> notes) {
  }

  /** One distinct value of a dimension, with its frequency, for the facet dropdowns. */
  public record FacetValue(String value, long count) {
  }

  public record FacetResult(String table, String column, List<FacetValue> values, boolean truncated,
                            long elapsedMs, String note) {
  }

  /**
   * What the UI needs to be honest about the data: the queryable surface per table, plus the window
   * actually present and the "populated from" caveats (status / apiKeyHash).
   */
  public record ColumnInfo(String key, String label, String kind, boolean groupable,
                           boolean aggregatable, String note) {
  }

  public record TableCoverage(String table, String sqlTable, long rowCount, String oldest, String newest,
                              List<ColumnInfo> columns) {
  }

  public record CoverageResult(List<TableCoverage> tables, List<String> notes) {
  }

  // ---- trace ---------------------------------------------------------------------------------

  /**
   * One span in a distributed trace: either a component's handling of the request, or a single Cypher
   * query underneath it. {@code offsetMs} is relative to the earliest span, so the UI can draw a
   * waterfall without doing date arithmetic.
   */
  public record TraceSpan(String kind,
                          String component,
                          String label,
                          String detail,
                          Integer status,
                          String startedAt,
                          long offsetMs,
                          double durationMs,
                          String localRequestId) {
  }

  /**
   * A whole request across the fleet. globalRequestId is deliberately non-unique in log_request — one
   * browser request fans out across components — so this resolves an id to every component that
   * handled it plus every query they ran.
   *
   * @param handlerMs   summed handler time across components (spans overlap, so this is not wall time)
   * @param dbMs        summed Cypher time
   * @param dbSharePct  dbMs as a percentage of handlerMs — a handler that is slow with a low share is
   *                    slow for reasons other than the database
   * @param spanMs      wall time from the first span's start to the last span's end
   */
  public record TraceResult(String globalRequestId,
                            List<TraceSpan> spans,
                            int requestCount,
                            int cypherCount,
                            int componentCount,
                            double handlerMs,
                            double dbMs,
                            double dbSharePct,
                            double spanMs,
                            boolean truncated,
                            long elapsedMs,
                            List<String> notes) {
  }
}
