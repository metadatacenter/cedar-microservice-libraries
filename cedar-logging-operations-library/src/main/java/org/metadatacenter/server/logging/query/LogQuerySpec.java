package org.metadatacenter.server.logging.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The wire contract for {@code POST /logs/query} — see
 * {@code cedar-development/ops/LOG-EXPLORER-UI-PLAN.md} §3.
 * <p>
 * Deliberately structured rather than SQL text: every column name is resolved through
 * {@link LogQueryColumns} and every value is bound as a parameter, so there is no injection surface
 * and the set of answerable questions is documented by the allowlist.
 * <p>
 * {@code groupBy} empty means raw-row mode (keyset-paged, newest first); non-empty means an
 * aggregate/pivot. Nulls are tolerated everywhere and normalized by {@link LogQueryBuilder}.
 * <p>
 * {@code source} selects {@code raw} (the log tables: exact, row-level, but only as far back as the
 * retention window) or {@code rollup} (the hourly agg_* tables: cheap, kept forever, hourly grain and
 * histogram-approximate percentiles). The rest of the spec is identical either way — that is the
 * point.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LogQuerySpec(String table,
                           String from,
                           String to,
                           List<Filter> filters,
                           List<String> groupBy,
                           List<String> metrics,
                           List<Sort> orderBy,
                           Integer limit,
                           String cursor,
                           List<Having> having,
                           String source) {

  /** Convenience for the common case of no HAVING and the raw source (keeps call sites readable). */
  public LogQuerySpec(String table, String from, String to, List<Filter> filters, List<String> groupBy,
                      List<String> metrics, List<Sort> orderBy, Integer limit, String cursor) {
    this(table, from, to, filters, groupBy, metrics, orderBy, limit, cursor, null, null);
  }

  public LogQuerySpec(String table, String from, String to, List<Filter> filters, List<String> groupBy,
                      List<String> metrics, List<Sort> orderBy, Integer limit, String cursor,
                      List<Having> having) {
    this(table, from, to, filters, groupBy, metrics, orderBy, limit, cursor, having, null);
  }

  /**
   * One predicate. {@code vals} is used by {@code in}/{@code notin}/{@code between}, {@code val} by
   * everything else; {@code isnull}/{@code notnull} need neither.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Filter(String col, String op, String val, List<String> vals) {
  }

  /**
   * Sort key: a dimension key in raw mode, or a dimension/metric key ("count", "sum:duration") in
   * grouped mode. {@code dir} is asc|desc (default desc).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Sort(String key, String dir) {
  }

  /**
   * A post-aggregation threshold on one of the requested metrics — the "only groups with more than k"
   * half of a pattern query (e.g. the N+1 detector: group by (globalRequestId, runnableHash) having
   * count &gt; 5). {@code op} is gt|gte|lt|lte|eq|ne.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Having(String key, String op, String val) {
  }

  public boolean isGrouped() {
    return groupBy != null && !groupBy.isEmpty();
  }
}
