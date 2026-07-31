package org.metadatacenter.server.logging.dao.query;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;
import org.metadatacenter.server.logging.query.LogQueryBuilder;
import org.metadatacenter.server.logging.query.LogQueryBuilder.BuiltQuery;
import org.metadatacenter.server.logging.query.LogQueryColumns;
import org.metadatacenter.server.logging.query.LogQueryColumns.ColumnDef;
import org.metadatacenter.server.logging.query.LogQueryColumns.Kind;
import org.metadatacenter.server.logging.query.LogQueryColumns.TableDef;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnInfo;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnMeta;
import org.metadatacenter.server.logging.query.LogQueryResults.CoverageResult;
import org.metadatacenter.server.logging.query.LogQueryResults.FacetResult;
import org.metadatacenter.server.logging.query.LogQueryResults.FacetValue;
import org.metadatacenter.server.logging.query.LogQueryResults.QueryResult;
import org.metadatacenter.server.logging.query.LogQueryResults.TableCoverage;
import org.metadatacenter.server.logging.query.LogQuerySpec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    List<Map<String, Object>> rows = new ArrayList<>(raw.size());
    for (Object r : raw) {
      Object[] cells = (r instanceof Object[] arr) ? arr : new Object[]{r};
      Map<String, Object> row = new LinkedHashMap<>();
      for (int i = 0; i < columns.size() && i < cells.length; i++) {
        row.put(columns.get(i).key(), normalize(cells[i]));
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
    return new QueryResult(columns, rows, rows.size(), truncated, nextCursor, elapsedMs, true,
        built.table().sqlTable(), notes);
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

  private static String iso(Object o) {
    Object v = normalize(o);
    return v == null ? null : String.valueOf(v);
  }

  /** Kinds that never belong in a facet list, exposed for the resource's error messages. */
  public static boolean isFacetable(Kind kind) {
    return kind == Kind.DIM;
  }
}
