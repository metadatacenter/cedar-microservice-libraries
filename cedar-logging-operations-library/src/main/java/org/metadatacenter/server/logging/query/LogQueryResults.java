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
}
