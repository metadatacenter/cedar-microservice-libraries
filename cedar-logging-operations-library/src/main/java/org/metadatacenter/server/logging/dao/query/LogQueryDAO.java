package org.metadatacenter.server.logging.dao.query;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;
import org.metadatacenter.server.logging.agg.LatencyHistogram;
import org.metadatacenter.server.logging.query.LogQueryBuilder;
import org.metadatacenter.server.logging.query.LogQueryBuilder.BuiltQuery;
import org.metadatacenter.server.logging.query.LogQueryColumns;
import org.metadatacenter.server.logging.query.LogQueryColumns.ColumnDef;
import org.metadatacenter.server.logging.query.LogQueryColumns.Kind;
import org.metadatacenter.server.logging.query.LogQueryColumns.TableDef;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnInfo;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnMeta;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnType;
import org.metadatacenter.server.logging.query.LogQueryResults.CoverageResult;
import org.metadatacenter.server.logging.query.LogQueryResults.FacetResult;
import org.metadatacenter.server.logging.query.LogQueryResults.FacetValue;
import org.metadatacenter.server.logging.query.LogQueryResults.QueryResult;
import org.metadatacenter.server.logging.query.LogQueryResults.TableCoverage;
import org.metadatacenter.server.logging.query.LogQueryResults.TraceResult;
import org.metadatacenter.server.logging.query.LogQueryResults.TraceSpan;
import org.metadatacenter.server.logging.query.LogQuerySpec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes {@link LogQueryBuilder} output against the raw log tables, and serves the two supporting
 * lookups the UI needs: facet values for the filter dropdowns, and coverage (what columns exist, what
 * time span is actually present) so the page can state its own limitations.
 * <p>
 * Deliberately thin: all SQL generation and validation lives in the builder, which is why this class
 * has no query text of its own beyond the facet/coverage helpers. Methods assume an active session
 * (the resource is {@code @UnitOfWork}).
 */
public class LogQueryDAO extends AbstractDAO<ApplicationRequestLog> {

  private static final int MAX_FACET_VALUES = 200;

  public LogQueryDAO(SessionFactory factory) {
    super(factory);
  }

  // ---- the engine --------------------------------------------------------------------------------

  public QueryResult query(LogQuerySpec spec) {
    BuiltQuery built = LogQueryBuilder.build(spec);

    NativeQuery<?> query = currentSession().createNativeQuery(built.sql());
    for (Map.Entry<String, Object> e : built.params().entrySet()) {
      query.setParameter(e.getKey(), e.getValue());
    }

    long started = System.nanoTime();
    List<?> raw = query.getResultList();
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

    List<ColumnMeta> columns = built.columns();
    int sqlValues = built.sqlValueCount();
    List<Double> fractions = built.percentileFractions();

    List<Map<String, Object>> rows = new ArrayList<>(raw.size());
    for (Object r : raw) {
      Object[] cells = (r instanceof Object[] arr) ? arr : new Object[]{r};
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 0; i < sqlValues && i < cells.length; i++) {
        row.put(columns.get(i).key(), normalize(cells[i]));
      }
      // Rollup percentiles have no SQL column: the query trails 15 merged histogram buckets, and the
      // ceilings are interpolated here — the same merge-then-percentile contract the rollups were
      // designed around (LatencyHistogram).
      if (!fractions.isEmpty() && cells.length >= sqlValues + LatencyHistogram.BUCKETS) {
        long[] hist = new long[LatencyHistogram.BUCKETS];
        for (int b = 0; b < LatencyHistogram.BUCKETS; b++) {
          hist[b] = num(cells[sqlValues + b]);
        }
        for (int p = 0; p < fractions.size(); p++) {
          row.put(columns.get(sqlValues + p).key(),
              LatencyHistogram.percentileNanos(hist, fractions.get(p)));
        }
      }
      rows.add(row);
    }

    boolean truncated = rows.size() >= built.limit();
    String nextCursor = null;
    if (built.keysetPageable() && truncated && !rows.isEmpty()) {
      Map<String, Object> last = rows.get(rows.size() - 1);
      Object ts = last.get(built.table().timeColumn());
      Object id = last.get("_id");
      if (ts instanceof String iso && id instanceof Number n) {
        nextCursor = LogQueryBuilder.formatCursor(Instant.parse(iso), n.longValue());
      }
    }

    List<String> notes = new ArrayList<>(built.notes());
    if (truncated) {
      notes.add("Result truncated at the limit of " + built.limit()
          + (nextCursor != null ? " — page with the returned cursor." : "."));
    }
    return new QueryResult(columns, rows, rows.size(), truncated, nextCursor, elapsedMs,
        !built.approximate(), built.table().sqlTable(), notes);
  }

  // ---- facets ------------------------------------------------------------------------------------

  /**
   * Distinct values of one dimension with their frequencies, for a filter dropdown. Bounded by the
   * time range and by {@link #MAX_FACET_VALUES} — a column that overflows that (path templates,
   * sessions) is meant to be filtered by text, not picked from a list.
   */
  public FacetResult facet(String tableKey, String columnKey, Instant from, Instant to) {
    TableDef table = LogQueryColumns.table(tableKey);
    ColumnDef def = table.column(columnKey);
    if (!def.groupable()) {
      throw new IllegalArgumentException("Column '" + columnKey + "' is " + def.kind()
          + " and has no facet values. Only dimensions do.");
    }
    String sql = "SELECT " + def.sql() + " AS v, COUNT(*) AS n FROM " + table.sqlTable()
        + " WHERE " + table.timeColumn() + " >= :from AND " + table.timeColumn() + " < :to"
        + " GROUP BY " + def.sql() + " ORDER BY n DESC LIMIT :lim";

    NativeQuery<?> query = currentSession().createNativeQuery(sql);
    query.setParameter("from", Timestamp.from(from));
    query.setParameter("to", Timestamp.from(to));
    query.setParameter("lim", MAX_FACET_VALUES + 1);

    long started = System.nanoTime();
    List<?> raw = query.getResultList();
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

    List<FacetValue> values = new ArrayList<>();
    boolean truncated = raw.size() > MAX_FACET_VALUES;
    int n = Math.min(raw.size(), MAX_FACET_VALUES);
    for (int i = 0; i < n; i++) {
      Object[] c = (Object[]) raw.get(i);
      values.add(new FacetValue(c[0] == null ? null : String.valueOf(normalize(c[0])), num(c[1])));
    }
    String note = truncated
        ? "More than " + MAX_FACET_VALUES + " distinct values in this range — use a text filter instead."
        : def.note();
    return new FacetResult(tableKey, columnKey, values, truncated, elapsedMs, note);
  }

  // ---- coverage ----------------------------------------------------------------------------------

  /**
   * The queryable surface plus the window actually present per table. This is what lets the UI say
   * "status is only populated from 2026-07-30" instead of rendering a column that looks broken.
   */
  public CoverageResult coverage() {
    List<TableCoverage> tables = new ArrayList<>();
    for (String key : LogQueryColumns.tableKeys()) {
      TableDef table = LogQueryColumns.table(key);
      String sql = "SELECT COUNT(*), MIN(" + table.timeColumn() + "), MAX(" + table.timeColumn() + ") FROM "
          + table.sqlTable();
      Object[] c = (Object[]) currentSession().createNativeQuery(sql).getSingleResult();

      List<ColumnInfo> columns = new ArrayList<>();
      for (ColumnDef def : table.columns().values()) {
        columns.add(new ColumnInfo(def.key(), def.label(), def.kind().name(), def.groupable(),
            def.aggregatable(), def.note()));
      }
      tables.add(new TableCoverage(key, table.sqlTable(), num(c[0]), iso(c[1]), iso(c[2]), columns));
    }
    List<String> notes = List.of(
        "status and apiKeyHash are populated only for rows written from 2026-07-30 onward; older rows are NULL.",
        "type and subType are single-valued in practice and are not useful facets.",
        "Raw rows are pruned on a retention window; longer ranges must be answered from the agg_* rollups.");
    return new CoverageResult(tables, notes);
  }

  // ---- trace -------------------------------------------------------------------------------------

  /**
   * Resolve one globalRequestId into a distributed trace: every component that handled the request,
   * plus every Cypher query underneath it, ordered on a shared timeline.
   *
   * This is the one query that genuinely cannot be expressed by the generic engine — it spans both
   * tables and computes cross-table totals — which is why it gets its own endpoint rather than
   * bending the spec. Both tables are indexed on globalRequestId, so each half is an index lookup.
   *
   * Note that globalRequestId is intentionally NOT unique in log_request: a single browser request
   * fans out across microservices and each logs its own row under the same id. That fan-out is
   * exactly what makes this view worth having.
   */
  public TraceResult trace(String globalRequestId, int maxSpans) {
    if (globalRequestId == null || globalRequestId.isBlank()) {
      throw new IllegalArgumentException("A globalRequestId is required.");
    }
    long started = System.nanoTime();

    List<Object[]> reqRows = traceRows(
        "SELECT systemComponentName, className, methodName, httpMethod, path, status, "
            + "COALESCE(startTime, requestTime), handlerDuration, localRequestId "
            + "FROM log_request WHERE globalRequestId = :grid ORDER BY COALESCE(startTime, requestTime)",
        globalRequestId, maxSpans + 1);

    List<Object[]> cypherRows = traceRows(
        "SELECT systemComponentName, operation, runnableHash, runnable, "
            + "COALESCE(startTime, logTime), duration, localRequestId "
            + "FROM log_cypher WHERE globalRequestId = :grid ORDER BY COALESCE(startTime, logTime)",
        globalRequestId, maxSpans + 1);

    boolean truncated = reqRows.size() > maxSpans || cypherRows.size() > maxSpans;
    if (reqRows.size() > maxSpans) {
      reqRows = reqRows.subList(0, maxSpans);
    }
    if (cypherRows.size() > maxSpans) {
      cypherRows = cypherRows.subList(0, maxSpans);
    }

    List<TraceSpan> spans = new ArrayList<>();
    Set<String> components = new LinkedHashSet<>();
    long handlerNanos = 0;
    long dbNanos = 0;

    for (Object[] r : reqRows) {
      String component = str(r[0]);
      components.add(component);
      handlerNanos += num(r[7]);
      spans.add(new TraceSpan("request", component,
          handler(str(r[1]), str(r[2])),
          (str(r[3]) == null ? "" : str(r[3]) + " ") + (str(r[4]) == null ? "" : str(r[4])),
          r[5] == null ? null : ((Number) r[5]).intValue(),
          iso(r[6]), 0L, num(r[7]) / 1_000_000.0, str(r[8])));
    }
    for (Object[] r : cypherRows) {
      components.add(str(r[0]));
      dbNanos += num(r[5]);
      spans.add(new TraceSpan("cypher", str(r[0]),
          str(r[1]) + " " + shortHash(str(r[2])),
          str(r[3]),
          null, iso(r[4]), 0L, num(r[5]) / 1_000_000.0, str(r[6])));
    }

    spans.sort(Comparator.comparing(s -> s.startedAt() == null ? "" : s.startedAt()));

    // Offsets are relative to the first span so the UI can draw a waterfall directly.
    long t0 = spans.isEmpty() ? 0 : epochMillis(spans.get(0).startedAt());
    double endMs = 0;
    List<TraceSpan> offset = new ArrayList<>(spans.size());
    for (TraceSpan s : spans) {
      long off = s.startedAt() == null ? 0 : epochMillis(s.startedAt()) - t0;
      endMs = Math.max(endMs, off + s.durationMs());
      offset.add(new TraceSpan(s.kind(), s.component(), s.label(), s.detail(), s.status(),
          s.startedAt(), off, s.durationMs(), s.localRequestId()));
    }

    double handlerMs = handlerNanos / 1_000_000.0;
    double dbMs = dbNanos / 1_000_000.0;
    List<String> notes = new ArrayList<>();
    if (offset.isEmpty()) {
      notes.add("No rows for this globalRequestId — it may have been pruned, or the id is wrong.");
    }
    if (truncated) {
      notes.add("Trace truncated at " + maxSpans + " spans per table.");
    }
    notes.add("Handler time sums overlapping component spans, so it exceeds wall time by design.");

    // Unclamped for the same reason as dbTimeShare: a share over 100% is a real signal about how the
    // request was logged, not something to hide behind a ceiling.
    return new TraceResult(globalRequestId, offset, reqRows.size(), cypherRows.size(),
        components.size(), handlerMs, dbMs,
        handlerMs > 0 ? dbMs / handlerMs * 100.0 : 0.0,
        endMs, truncated, (System.nanoTime() - started) / 1_000_000L, notes);
  }

  @SuppressWarnings("unchecked")
  private List<Object[]> traceRows(String sql, String globalRequestId, int limit) {
    NativeQuery<?> q = currentSession().createNativeQuery(sql + " LIMIT :lim");
    q.setParameter("grid", globalRequestId);
    q.setParameter("lim", limit);
    return (List<Object[]>) q.getResultList();
  }

  private static long epochMillis(String iso) {
    try {
      return Instant.parse(iso).toEpochMilli();
    } catch (RuntimeException e) {
      return 0L;
    }
  }

  private static String handler(String cls, String mth) {
    if (cls == null && mth == null) {
      return null;
    }
    return (cls == null ? "?" : cls) + "." + (mth == null ? "?" : mth);
  }

  private static String shortHash(String h) {
    return h == null ? "" : (h.length() > 10 ? h.substring(0, 10) : h);
  }

  // ---- db-time share -----------------------------------------------------------------------------

  /**
   * Per handler: total handler time vs the Cypher time underneath it, joined on globalRequestId.
   * <p>
   * The question this answers is "which slow handlers are NOT database-bound" — a handler with a high
   * total and a low share is spending its time somewhere else (BioPortal, OpenSearch, serialization),
   * and no single-table query can tell you that. Returns a {@link QueryResult} so the existing
   * ColumnMeta-driven table renders it exactly like a board.
   * <p>
   * Cypher time is pre-aggregated per (globalRequestId, component) and joined on both columns, so a
   * component is only charged for the queries it actually ran during that request. The remaining
   * imprecision is stated in the notes rather than hidden: if one component logs several request rows
   * under the same globalRequestId, that request's DB time is counted against each of them.
   */
  public QueryResult dbTimeShare(Instant from, Instant to, int limit) {
    String sql =
        "SELECT CONCAT(COALESCE(r.className,'?'),'.',COALESCE(r.methodName,'?')) AS h, "
            + "r.systemComponentName AS c, COUNT(*) AS n, SUM(r.handlerDuration) AS hd, "
            + "COALESCE(SUM(q.dbNanos),0) AS db "
            + "FROM log_request r LEFT JOIN ("
            + "  SELECT globalRequestId, systemComponentName, SUM(duration) AS dbNanos FROM log_cypher"
            + "  WHERE logTime >= :from AND logTime < :to AND globalRequestId IS NOT NULL"
            + "  GROUP BY globalRequestId, systemComponentName"
            + ") q ON q.globalRequestId = r.globalRequestId AND q.systemComponentName = r.systemComponentName "
            + "WHERE r.requestTime >= :from AND r.requestTime < :to "
            + "GROUP BY h, c ORDER BY hd DESC LIMIT :lim";

    NativeQuery<?> query = currentSession().createNativeQuery(sql);
    query.setParameter("from", Timestamp.from(from));
    query.setParameter("to", Timestamp.from(to));
    query.setParameter("lim", limit);

    long started = System.nanoTime();
    List<?> raw = query.getResultList();
    long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

    List<ColumnMeta> columns = List.of(
        new ColumnMeta("handler", "Handler", ColumnType.STRING, null),
        new ColumnMeta("component", "Component", ColumnType.STRING, null),
        new ColumnMeta("count", "Requests", ColumnType.NUMBER, null),
        new ColumnMeta("sum:handlerDuration", "Total handler", ColumnType.NANOS, "nanos"),
        new ColumnMeta("sum:duration", "Of which database", ColumnType.NANOS, "nanos"),
        new ColumnMeta("dbSharePct", "% in Neo4j", ColumnType.NUMBER, "database share of handler time"));

    List<Map<String, Object>> rows = new ArrayList<>();
    for (Object o : raw) {
      Object[] c = (Object[]) o;
      long handlerNanos = num(c[3]);
      long dbNanos = num(c[4]);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("handler", str(c[0]));
      row.put("component", str(c[1]));
      row.put("count", num(c[2]));
      row.put("sum:handlerDuration", handlerNanos);
      row.put("sum:duration", dbNanos);
      // NOT clamped to 100: a share above 100% is real information, not a rendering glitch. It means
      // one component logged several request rows under the same globalRequestId, so that request's
      // DB time was counted against each of them. Clamping would silently disguise over-attribution
      // as "entirely database-bound".
      row.put("dbSharePct", handlerNanos > 0
          ? Math.round(dbNanos * 1000.0 / handlerNanos) / 10.0 : 0.0);
      rows.add(row);
    }

    boolean overAttributed = rows.stream()
        .anyMatch(r -> r.get("dbSharePct") instanceof Number n && n.doubleValue() > 100.0);

    List<String> notes = new ArrayList<>();
    notes.add("Database time is joined on (globalRequestId, component), so each component is charged "
        + "only for the queries it ran.");
    notes.add("A high total with a LOW share means the handler is slow for reasons other than Neo4j.");
    if (overAttributed) {
      notes.add("Some shares exceed 100%: those handlers log several request rows under one "
          + "globalRequestId, so the request's database time is counted against each row. Treat their "
          + "share as an upper bound.");
    }
    boolean truncated = rows.size() >= limit;
    if (truncated) {
      notes.add("Truncated at " + limit + " handlers.");
    }
    return new QueryResult(columns, rows, rows.size(), truncated, null, elapsedMs, true,
        "log_request ⨝ log_cypher", notes);
  }

  // ---- value normalization -----------------------------------------------------------------------

  /**
   * JDBC hands back vendor types (BigInteger/BigDecimal counts, Timestamp, byte[] for LOB-ish reads).
   * Normalize to what Jackson should emit: numbers as long/double, timestamps as ISO-8601 strings so
   * the UI never has to guess a timezone.
   */
  private static Object normalize(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Timestamp t) {
      return t.toInstant().toString();
    }
    if (o instanceof java.sql.Date d) {
      return d.toString();
    }
    if (o instanceof BigInteger b) {
      return b.longValue();
    }
    if (o instanceof BigDecimal b) {
      return b.stripTrailingZeros().scale() <= 0 ? (Object) b.longValue() : (Object) b.doubleValue();
    }
    if (o instanceof byte[] b) {
      return new String(b);
    }
    return o;
  }

  private static long num(Object o) {
    return o == null ? 0L : ((Number) o).longValue();
  }

  private static String str(Object o) {
    Object v = normalize(o);
    return v == null ? null : String.valueOf(v);
  }

  private static String iso(Object o) {
    Object v = normalize(o);
    return v == null ? null : String.valueOf(v);
  }

  /** Kinds that never belong in a facet list, exposed for the resource's error messages. */
  public static boolean isFacetable(Kind kind) {
    return kind == Kind.DIM;
  }
}
