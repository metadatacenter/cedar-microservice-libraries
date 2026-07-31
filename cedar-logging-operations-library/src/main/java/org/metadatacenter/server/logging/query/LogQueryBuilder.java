package org.metadatacenter.server.logging.query;

import org.metadatacenter.server.logging.query.LogQueryColumns.ColumnDef;
import org.metadatacenter.server.logging.query.LogQueryColumns.Kind;
import org.metadatacenter.server.logging.query.LogQueryColumns.TableDef;
import org.metadatacenter.server.logging.query.LogQueryColumns.ValType;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnMeta;
import org.metadatacenter.server.logging.query.LogQueryResults.ColumnType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Translates a {@link LogQuerySpec} into parameterized MySQL. Pure function, no session — that is the
 * point: the whole SQL-generation contract is unit-testable without a database, and it is the only
 * place SQL text is assembled.
 * <p>
 * Safety model: identifiers and expressions come exclusively from {@link LogQueryColumns}; every
 * caller-supplied value becomes a named bind parameter. Nothing from the request body is ever
 * concatenated into SQL.
 * <p>
 * Two shapes are emitted (see {@code LOG-EXPLORER-UI-PLAN.md} §3):
 * <ul>
 *   <li><b>raw</b> ({@code groupBy} empty) — the display columns, newest first, keyset-paged on
 *       (timeColumn, id) so page N costs the same as page 1.</li>
 *   <li><b>grouped</b> — GROUP BY the requested dimensions with the requested metrics. When a
 *       percentile is asked for, the query is wrapped: an inner SELECT adds
 *       {@code ROW_NUMBER()/COUNT() OVER (PARTITION BY dims)} and the outer picks the
 *       ceil(p·n)-th row. MySQL 8 has no PERCENTILE_CONT, and this is exact rather than
 *       histogram-approximate.</li>
 * </ul>
 */
public final class LogQueryBuilder {

  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_RAW_LIMIT = 2000;
  public static final int MAX_GROUPED_LIMIT = 500;
  /** Guard against an accidentally unbounded scan; raw retention is 30d anyway. */
  public static final int MAX_SPAN_DAYS = 400;
  private static final Duration DEFAULT_SPAN = Duration.ofHours(24);

  private static final Set<String> OPS = Set.of(
      "eq", "ne", "in", "notin", "like", "notlike", "startswith", "gte", "lte", "between", "isnull", "notnull");
  private static final Set<String> PERCENTILES = Set.of("p50", "p90", "p95", "p99");
  private static final Set<String> PLAIN_AGGS = Set.of("sum", "avg", "min", "max");

  private LogQueryBuilder() {
  }

  /** SQL plus everything the DAO and the UI need to interpret the result. */
  public record BuiltQuery(String sql,
                           Map<String, Object> params,
                           List<ColumnMeta> columns,
                           TableDef table,
                           boolean grouped,
                           boolean keysetPageable,
                           int limit,
                           Instant from,
                           Instant to,
                           List<String> notes) {
  }

  /** A parsed metric: {@code count}, {@code sum:duration}, {@code p95:handlerDuration}, … */
  private record Metric(String key, String fn, String col) {
  }

  private record Cursor(Instant ts, long id) {
  }

  // ---- entry point -------------------------------------------------------------------------------

  public static BuiltQuery build(LogQuerySpec spec) {
    if (spec == null) {
      throw new IllegalArgumentException("Missing query spec.");
    }
    TableDef table = LogQueryColumns.table(spec.table() == null ? LogQueryColumns.T_REQUEST : spec.table());

    Instant to = spec.to() == null ? Instant.now() : parseInstant(spec.to(), "to");
    Instant from = spec.from() == null ? to.minus(DEFAULT_SPAN) : parseInstant(spec.from(), "from");
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("'from' must be before 'to' (got from=" + from + ", to=" + to + ").");
    }
    if (Duration.between(from, to).toDays() > MAX_SPAN_DAYS) {
      throw new IllegalArgumentException("Range too wide: " + Duration.between(from, to).toDays()
          + " days, maximum is " + MAX_SPAN_DAYS + ". Narrow the range.");
    }

    List<String> notes = new ArrayList<>();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("from", Timestamp.from(from));
    params.put("to", Timestamp.from(to));

    StringBuilder where = new StringBuilder(table.timeColumn() + " >= :from AND " + table.timeColumn() + " < :to");
    appendFilters(spec, table, where, params, notes);

    // Metrics without groupBy is the totals shape (one row, no GROUP BY) — that is what the KPI tiles
    // on a board are. Only a spec with neither is a raw-row query.
    boolean aggregate = spec.isGrouped() || (spec.metrics() != null && !spec.metrics().isEmpty());
    return aggregate
        ? grouped(spec, table, where.toString(), params, from, to, notes)
        : raw(spec, table, where, params, from, to, notes);
  }

  // ---- raw rows ----------------------------------------------------------------------------------

  private static BuiltQuery raw(LogQuerySpec spec, TableDef table, StringBuilder where,
                                Map<String, Object> params, Instant from, Instant to, List<String> notes) {
    if (spec.having() != null && !spec.having().isEmpty()) {
      throw new IllegalArgumentException(
          "Having needs metrics; it filters aggregated groups, not raw rows.");
    }
    int limit = clampLimit(spec.limit(), MAX_RAW_LIMIT);

    // Custom sort and keyset paging are mutually exclusive: the cursor only means something under the
    // (time, id) ordering it was produced by.
    boolean defaultSort = spec.orderBy() == null || spec.orderBy().isEmpty();
    if (!defaultSort && spec.cursor() != null) {
      throw new IllegalArgumentException(
          "A cursor is only valid with the default sort; drop orderBy to page, or drop the cursor to sort.");
    }
    if (spec.cursor() != null) {
      Cursor cursor = parseCursor(spec.cursor());
      where.append(" AND (").append(table.timeColumn()).append(" < :curTs OR (")
          .append(table.timeColumn()).append(" = :curTs AND ").append(table.idColumn()).append(" < :curId))");
      params.put("curTs", Timestamp.from(cursor.ts()));
      params.put("curId", cursor.id());
    }

    List<ColumnMeta> columns = new ArrayList<>();
    StringBuilder select = new StringBuilder("SELECT ").append(table.idColumn()).append(" AS `_id`");
    columns.add(new ColumnMeta("_id", "Row id", ColumnType.NUMBER, "internal, used for paging"));
    for (String key : table.rowColumns()) {
      ColumnDef def = table.column(key);
      select.append(", ").append(def.sql()).append(" AS ").append(quote(key));
      columns.add(meta(key, def));
    }

    String order;
    if (defaultSort) {
      order = table.timeColumn() + " DESC, " + table.idColumn() + " DESC";
    } else {
      order = orderClause(spec, table, Set.of(), List.of(), true);
      notes.add("Custom sort in raw mode: paging is disabled (no cursor is returned).");
    }

    String sql = select + " FROM " + table.sqlTable() + " WHERE " + where + " ORDER BY " + order + " LIMIT :lim";
    params.put("lim", limit);
    return new BuiltQuery(sql, params, columns, table, false, defaultSort, limit, from, to, notes);
  }

  // ---- grouped / pivot ---------------------------------------------------------------------------

  private static BuiltQuery grouped(LogQuerySpec spec, TableDef table, String where,
                                    Map<String, Object> params, Instant from, Instant to, List<String> notes) {
    // groupBy may be null/empty — that is the totals shape (metrics over the whole range, one row)
    List<String> dims = spec.groupBy() == null
        ? new ArrayList<>() : new ArrayList<>(new LinkedHashSet<>(spec.groupBy()));
    for (String key : dims) {
      ColumnDef def = table.column(key);
      if (!def.groupable()) {
        throw new IllegalArgumentException("Column '" + key + "' is " + def.kind()
            + " and cannot be grouped." + (def.kind() == Kind.TIME ? " Group by tsMinute/tsHour/tsDay instead." : "")
            + (def.kind() == Kind.TEXT ? " It is a LONGTEXT; filter it instead." : ""));
      }
    }

    List<Metric> metrics = parseMetrics(spec.metrics(), table);
    List<String> pctCols = new ArrayList<>();
    for (Metric m : metrics) {
      if (PERCENTILES.contains(m.fn()) && !pctCols.contains(m.col())) {
        pctCols.add(m.col());
      }
    }
    int limit = clampLimit(spec.limit(), MAX_GROUPED_LIMIT);

    List<ColumnMeta> columns = new ArrayList<>();
    for (String key : dims) {
      columns.add(meta(key, table.column(key)));
    }
    for (Metric m : metrics) {
      columns.add(metricMeta(m, table));
    }

    String sql = pctCols.isEmpty()
        ? flatGrouped(table, dims, metrics, where, limit)
        : windowedGrouped(table, dims, metrics, pctCols, where, limit);
    if (!pctCols.isEmpty()) {
      notes.add("Percentiles are exact (window function over the raw rows), not histogram estimates.");
    }

    String order = orderClause(spec, table, metricKeys(metrics), dims, false);
    sql = sql.replace("/*HAVING*/", havingClause(spec, metricKeys(metrics), params))
        .replace("/*ORDER*/", order);
    params.put("lim", limit);
    return new BuiltQuery(sql, params, columns, table, true, false, limit, from, to, notes);
  }

  /** No percentiles: aggregate straight over the table. */
  private static String flatGrouped(TableDef table, List<String> dims, List<Metric> metrics,
                                    String where, int limit) {
    StringBuilder select = new StringBuilder("SELECT ");
    for (String key : dims) {
      select.append(table.column(key).sql()).append(" AS ").append(quote(key)).append(", ");
    }
    appendMetricExprs(select, metrics, table, null);

    StringBuilder sql = new StringBuilder(select).append(" FROM ").append(table.sqlTable())
        .append(" WHERE ").append(where);
    if (!dims.isEmpty()) {
      sql.append(" GROUP BY ");
      for (int i = 0; i < dims.size(); i++) {
        sql.append(i == 0 ? "" : ", ").append(table.column(dims.get(i)).sql());
      }
    }
    return sql.append("/*HAVING*/ ORDER BY /*ORDER*/ LIMIT :lim").toString();
  }

  /**
   * With percentiles: the inner SELECT ranks every row inside its group, the outer picks the
   * ceil(p·n)-th. One ROW_NUMBER/COUNT pair per distinct percentile target column.
   */
  private static String windowedGrouped(TableDef table, List<String> dims, List<Metric> metrics,
                                        List<String> pctCols, String where, int limit) {
    String partition = partitionClause(table, dims);

    StringBuilder inner = new StringBuilder("SELECT ");
    for (String key : dims) {
      inner.append(table.column(key).sql()).append(" AS ").append(quote(key)).append(", ");
    }
    // every column any metric touches must survive into the derived table
    Set<String> carried = new LinkedHashSet<>();
    for (Metric m : metrics) {
      if (m.col() != null) {
        carried.add(m.col());
      }
    }
    for (String col : carried) {
      inner.append(table.column(col).sql()).append(" AS ").append(quote(valAlias(col))).append(", ");
    }
    for (int i = 0; i < pctCols.size(); i++) {
      String col = pctCols.get(i);
      String colSql = table.column(col).sql();
      inner.append("ROW_NUMBER() OVER (").append(partition).append(" ORDER BY ").append(colSql)
          .append(") AS ").append(quote(rnAlias(col))).append(", ")
          .append("COUNT(*) OVER (").append(partition).append(") AS ").append(quote(cntAlias(col)))
          .append(i == pctCols.size() - 1 ? "" : ", ");
    }
    inner.append(" FROM ").append(table.sqlTable()).append(" WHERE ").append(where);

    StringBuilder outer = new StringBuilder("SELECT ");
    for (String key : dims) {
      outer.append(quote(key)).append(", ");
    }
    appendMetricExprs(outer, metrics, table, LogQueryBuilder::valAlias);

    outer.append(" FROM (").append(inner).append(") `x`");
    if (!dims.isEmpty()) {
      outer.append(" GROUP BY ");
      for (int i = 0; i < dims.size(); i++) {
        outer.append(i == 0 ? "" : ", ").append(quote(dims.get(i)));
      }
    }
    return outer.append("/*HAVING*/ ORDER BY /*ORDER*/ LIMIT :lim").toString();
  }

  private static String partitionClause(TableDef table, List<String> dims) {
    if (dims.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder("PARTITION BY ");
    for (int i = 0; i < dims.size(); i++) {
      sb.append(i == 0 ? "" : ", ").append(table.column(dims.get(i)).sql());
    }
    return sb.toString();
  }

  /**
   * Emit the metric select list. {@code ref} maps a column key to how it is referenced: null means
   * "use the table expression" (flat mode), otherwise the carried alias (windowed mode).
   */
  private static void appendMetricExprs(StringBuilder select, List<Metric> metrics, TableDef table,
                                        java.util.function.Function<String, String> ref) {
    for (int i = 0; i < metrics.size(); i++) {
      Metric m = metrics.get(i);
      String colRef = m.col() == null ? null : (ref == null ? table.column(m.col()).sql() : quote(ref.apply(m.col())));
      String expr;
      if ("count".equals(m.fn())) {
        expr = "COUNT(*)";
      } else if ("distinct".equals(m.fn())) {
        expr = "COUNT(DISTINCT " + colRef + ")";
      } else if (PLAIN_AGGS.contains(m.fn())) {
        expr = m.fn().toUpperCase(Locale.ROOT) + "(" + colRef + ")";
      } else {
        // percentile: only reachable in windowed mode, where rn/cnt aliases exist
        double p = Integer.parseInt(m.fn().substring(1)) / 100.0;
        expr = "MAX(CASE WHEN " + quote(rnAlias(m.col())) + " = GREATEST(1, CEIL(" + p + " * "
            + quote(cntAlias(m.col())) + ")) THEN " + colRef + " END)";
      }
      select.append(expr).append(" AS ").append(quote(sqlAlias(m.key())))
          .append(i == metrics.size() - 1 ? "" : ", ");
    }
  }

  // ---- filters -----------------------------------------------------------------------------------

  private static void appendFilters(LogQuerySpec spec, TableDef table, StringBuilder where,
                                    Map<String, Object> params, List<String> notes) {
    if (spec.filters() == null) {
      return;
    }
    int n = 0;
    for (LogQuerySpec.Filter f : spec.filters()) {
      if (f == null || f.col() == null) {
        throw new IllegalArgumentException("Filter is missing 'col'.");
      }
      ColumnDef def = table.column(f.col());
      String op = f.op() == null ? "eq" : f.op().toLowerCase(Locale.ROOT);
      if (!OPS.contains(op)) {
        throw new IllegalArgumentException("Unknown filter op '" + op + "'. Expected one of: "
            + String.join(", ", new java.util.TreeSet<>(OPS)));
      }
      String sql = def.sql();
      String p = "f" + n++;

      switch (op) {
        case "isnull" -> where.append(" AND ").append(sql).append(" IS NULL");
        case "notnull" -> where.append(" AND ").append(sql).append(" IS NOT NULL");
        case "in", "notin" -> {
          List<String> vals = requireVals(f, op);
          where.append(" AND ").append(sql).append("notin".equals(op) ? " NOT IN (" : " IN (");
          for (int i = 0; i < vals.size(); i++) {
            String pi = p + "_" + i;
            where.append(i == 0 ? "" : ", ").append(":").append(pi);
            params.put(pi, coerce(def, vals.get(i)));
          }
          where.append(")");
        }
        case "between" -> {
          List<String> vals = requireVals(f, op);
          if (vals.size() != 2) {
            throw new IllegalArgumentException("Op 'between' needs exactly 2 values, got " + vals.size() + ".");
          }
          where.append(" AND ").append(sql).append(" BETWEEN :").append(p).append("_lo AND :").append(p).append("_hi");
          params.put(p + "_lo", coerce(def, vals.get(0)));
          params.put(p + "_hi", coerce(def, vals.get(1)));
        }
        case "like", "notlike" -> {
          where.append(" AND ").append(sql).append("notlike".equals(op) ? " NOT LIKE :" : " LIKE :").append(p);
          params.put(p, "%" + requireVal(f, op) + "%");
          if (def.kind() != Kind.TEXT) {
            notes.add("Leading-wildcard LIKE on '" + f.col() + "' cannot use an index; consider 'startswith' or a facet.");
          }
        }
        case "startswith" -> {
          where.append(" AND ").append(sql).append(" LIKE :").append(p);
          params.put(p, requireVal(f, op) + "%");
        }
        default -> {
          String cmp = switch (op) {
            case "eq" -> " = :";
            case "ne" -> " <> :";
            case "gte" -> " >= :";
            case "lte" -> " <= :";
            default -> throw new IllegalStateException("unreachable op " + op);
          };
          where.append(" AND ").append(sql).append(cmp).append(p);
          params.put(p, coerce(def, requireVal(f, op)));
        }
      }
    }
  }

  // ---- having ------------------------------------------------------------------------------------

  /**
   * Post-aggregation thresholds on the requested metrics. This is what makes pattern boards possible
   * without bespoke SQL — the N+1 detector is "group by (globalRequestId, runnableHash) having count
   * &gt; 5". The key must be one of the metrics actually selected, since HAVING references its alias.
   */
  private static String havingClause(LogQuerySpec spec, Set<String> metricKeys, Map<String, Object> params) {
    if (spec.having() == null || spec.having().isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(" HAVING ");
    int n = 0;
    for (LogQuerySpec.Having h : spec.having()) {
      if (h == null || h.key() == null) {
        throw new IllegalArgumentException("Having entry is missing 'key'.");
      }
      if (!metricKeys.contains(h.key())) {
        throw new IllegalArgumentException("Cannot filter on '" + h.key()
            + "': having must reference one of the requested metrics (" + String.join(", ", metricKeys) + ").");
      }
      String op = h.op() == null ? "gt" : h.op().toLowerCase(Locale.ROOT);
      String cmp = switch (op) {
        case "gt" -> " > :";
        case "gte" -> " >= :";
        case "lt" -> " < :";
        case "lte" -> " <= :";
        case "eq" -> " = :";
        case "ne" -> " <> :";
        default -> throw new IllegalArgumentException(
            "Unknown having op '" + op + "'. Expected one of: eq, gt, gte, lt, lte, ne.");
      };
      if (h.val() == null) {
        throw new IllegalArgumentException("Having on '" + h.key() + "' needs 'val'.");
      }
      String p = "h" + n;
      sb.append(n++ == 0 ? "" : " AND ").append(quote(sqlAlias(h.key()))).append(cmp).append(p);
      params.put(p, parseNumber(h.val(), h.key()));
    }
    return sb.toString();
  }

  // ---- ordering ----------------------------------------------------------------------------------

  /**
   * Raw mode sorts by the column expression (the select list only aliases the display columns, so an
   * alias is not guaranteed to exist); grouped mode sorts by alias, and the key must therefore be one
   * of the requested metrics or grouped dimensions — anything else has no alias to order by.
   */
  private static String orderClause(LogQuerySpec spec, TableDef table, Set<String> metricKeys,
                                    List<String> dims, boolean raw) {
    if (spec.orderBy() == null || spec.orderBy().isEmpty()) {
      return raw
          ? table.timeColumn() + " DESC, " + table.idColumn() + " DESC"
          : quote(sqlAlias(metricKeys.iterator().next())) + " DESC";
    }
    StringBuilder sb = new StringBuilder();
    int i = 0;
    for (LogQuerySpec.Sort s : spec.orderBy()) {
      if (s == null || s.key() == null) {
        throw new IllegalArgumentException("Sort entry is missing 'key'.");
      }
      String term;
      if (raw) {
        term = table.column(s.key()).sql();
      } else if (metricKeys.contains(s.key()) || dims.contains(s.key())) {
        term = quote(sqlAlias(s.key()));
      } else {
        throw new IllegalArgumentException("Cannot sort by '" + s.key()
            + "': in grouped mode order by one of the requested metrics (" + String.join(", ", metricKeys)
            + ") or grouped dimensions (" + String.join(", ", dims) + ").");
      }
      boolean desc = s.dir() == null || !"asc".equalsIgnoreCase(s.dir());
      sb.append(i++ == 0 ? "" : ", ").append(term).append(desc ? " DESC" : " ASC");
    }
    return sb.toString();
  }

  // ---- metrics -----------------------------------------------------------------------------------

  private static List<Metric> parseMetrics(List<String> raw, TableDef table) {
    List<String> keys = (raw == null || raw.isEmpty()) ? List.of("count") : raw;
    List<Metric> out = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    for (String key : keys) {
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("Empty metric.");
      }
      String k = key.trim();
      if (!seen.add(k)) {
        continue;
      }
      if ("count".equalsIgnoreCase(k)) {
        out.add(new Metric("count", "count", null));
        continue;
      }
      int colon = k.indexOf(':');
      if (colon < 0) {
        throw new IllegalArgumentException("Malformed metric '" + k
            + "'. Expected 'count' or '<fn>:<column>' where fn is distinct|sum|avg|min|max|p50|p90|p95|p99.");
      }
      String fn = k.substring(0, colon).toLowerCase(Locale.ROOT);
      String col = k.substring(colon + 1);
      ColumnDef def = table.column(col);
      if ("distinct".equals(fn)) {
        if (def.kind() == Kind.TEXT) {
          throw new IllegalArgumentException("distinct on '" + col + "' is not allowed (LONGTEXT).");
        }
      } else if (PLAIN_AGGS.contains(fn) || PERCENTILES.contains(fn)) {
        if (!def.aggregatable()) {
          throw new IllegalArgumentException("Column '" + col + "' is " + def.kind()
              + " and cannot be used with '" + fn + "'. Numeric columns only.");
        }
      } else {
        throw new IllegalArgumentException("Unknown metric function '" + fn + "' in '" + k + "'.");
      }
      out.add(new Metric(k, fn, col));
    }
    return out;
  }

  private static Set<String> metricKeys(List<Metric> metrics) {
    Set<String> keys = new LinkedHashSet<>();
    for (Metric m : metrics) {
      keys.add(m.key());
    }
    return keys;
  }

  // ---- cursors -----------------------------------------------------------------------------------

  public static String formatCursor(Instant ts, long id) {
    return ts.toString() + "," + id;
  }

  private static Cursor parseCursor(String cursor) {
    int comma = cursor.lastIndexOf(',');
    if (comma <= 0) {
      throw new IllegalArgumentException("Malformed cursor '" + cursor + "'. Expected '<iso-instant>,<id>'.");
    }
    try {
      return new Cursor(Instant.parse(cursor.substring(0, comma)), Long.parseLong(cursor.substring(comma + 1)));
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new IllegalArgumentException("Malformed cursor '" + cursor + "'. Expected '<iso-instant>,<id>'.");
    }
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private static ColumnMeta meta(String key, ColumnDef def) {
    return new ColumnMeta(key, def.label(), uiType(def), def.note());
  }

  private static ColumnMeta metricMeta(Metric m, TableDef table) {
    if (m.col() == null) {
      return new ColumnMeta(m.key(), "Count", ColumnType.NUMBER, null);
    }
    ColumnDef def = table.column(m.col());
    boolean nanos = "nanos".equals(def.note()) && !"distinct".equals(m.fn());
    String label = switch (m.fn()) {
      case "distinct" -> "Distinct " + def.label().toLowerCase(Locale.ROOT);
      case "sum" -> "Total " + def.label().toLowerCase(Locale.ROOT);
      case "avg" -> "Avg " + def.label().toLowerCase(Locale.ROOT);
      case "min" -> "Min " + def.label().toLowerCase(Locale.ROOT);
      case "max" -> "Max " + def.label().toLowerCase(Locale.ROOT);
      default -> m.fn() + " " + def.label().toLowerCase(Locale.ROOT);
    };
    return new ColumnMeta(m.key(), label, nanos ? ColumnType.NANOS : ColumnType.NUMBER, def.note());
  }

  private static ColumnType uiType(ColumnDef def) {
    return switch (def.kind()) {
      case NUM -> "nanos".equals(def.note()) ? ColumnType.NANOS : ColumnType.NUMBER;
      case TIME -> ColumnType.TIMESTAMP;
      case TEXT -> ColumnType.TEXT;
      case DIM -> def.type() == ValType.LONG ? ColumnType.NUMBER : ColumnType.STRING;
    };
  }

  private static Object coerce(ColumnDef def, String value) {
    if (value == null) {
      throw new IllegalArgumentException("Null value for column '" + def.key() + "'.");
    }
    return switch (def.type()) {
      case STRING -> value;
      case LONG -> parseLong(value, def.key());
      case TIMESTAMP -> Timestamp.from(parseInstant(value, def.key()));
    };
  }

  /** Metric thresholds may be fractional (avg, a ratio), so accept both. */
  private static Number parseNumber(String value, String what) {
    try {
      String v = value.trim();
      return v.indexOf('.') >= 0 ? (Number) Double.valueOf(v) : (Number) Long.valueOf(v);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Value '" + value + "' for '" + what + "' is not a number.");
    }
  }

  private static long parseLong(String value, String what) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Value '" + value + "' for '" + what + "' is not a number.");
    }
  }

  private static Instant parseInstant(String value, String what) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException("Value '" + value + "' for '" + what
          + "' is not an ISO-8601 instant (e.g. 2026-07-31T12:00:00Z).");
    }
  }

  private static String requireVal(LogQuerySpec.Filter f, String op) {
    if (f.val() == null) {
      throw new IllegalArgumentException("Op '" + op + "' on '" + f.col() + "' needs 'val'.");
    }
    return f.val();
  }

  private static List<String> requireVals(LogQuerySpec.Filter f, String op) {
    if (f.vals() == null || f.vals().isEmpty()) {
      throw new IllegalArgumentException("Op '" + op + "' on '" + f.col() + "' needs a non-empty 'vals'.");
    }
    return f.vals();
  }

  private static int clampLimit(Integer limit, int max) {
    if (limit == null || limit <= 0) {
      return Math.min(DEFAULT_LIMIT, max);
    }
    return Math.min(limit, max);
  }

  /**
   * Aliases are backticked because some keys collide with SQL keywords ("count", "status").
   * <p>
   * A colon must never survive into the SQL text — not even inside backticks or a string literal.
   * These queries run as Hibernate <em>native</em> queries, and Hibernate scans the text for
   * {@code :name} parameters before MySQL sees it, so a colon in an alias becomes a phantom bind
   * parameter and the query fails with "Named parameter not bound". Bind parameters are the only
   * legitimate colons here.
   */
  private static String quote(String alias) {
    if (alias.indexOf('`') >= 0 || alias.indexOf(':') >= 0) {
      throw new IllegalArgumentException("Illegal alias '" + alias + "'.");
    }
    return "`" + alias + "`";
  }

  /**
   * The SQL alias for a metric. The public key keeps its readable "fn:column" form in the response
   * columns (rows are assembled positionally, so the two need not match) while the SQL alias is
   * colon-free — see {@link #quote(String)}.
   */
  private static String sqlAlias(String metricKey) {
    return metricKey.replace(':', '_');
  }

  private static String valAlias(String col) {
    return "v_" + col;
  }

  private static String rnAlias(String col) {
    return "rn_" + col;
  }

  private static String cntAlias(String col) {
    return "cnt_" + col;
  }
}
