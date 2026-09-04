package org.metadatacenter.server.logging.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The column allowlist for the structured log query engine — see
 * {@code cedar-development/ops/LOG-EXPLORER-UI-PLAN.md} §3.
 * <p>
 * This class is the security boundary: {@link LogQueryBuilder} only ever emits SQL identifiers and
 * expressions that come from here, and every value the caller supplies is bound as a parameter. A
 * column absent from this registry cannot be filtered, grouped or aggregated — the builder rejects
 * it by name. It doubles as the "which columns are real" documentation the UI reads through
 * {@code GET /logs/coverage}.
 * <p>
 * Derived dimensions (handler, pathTemplate, statusClass, the tsX time buckets, hourOfDay) are plain
 * SQL expressions rather than stored columns; they exist because grouping on the raw column would be
 * either useless (1,165 distinct paths) or unavailable (no status before 2026-07-30).
 */
public final class LogQueryColumns {

  public static final String T_REQUEST = "request";
  public static final String T_CYPHER = "cypher";

  public static final String SOURCE_RAW = "raw";
  public static final String SOURCE_ROLLUP = "rollup";
  private static final String ROLLUP_SUFFIX = "-rollup";

  /** How a column may be used. Filtering is allowed for every kind; the rest depends on this. */
  public enum Kind {
    /** Groupable and filterable: low cardinality, or a derived bucket. */
    DIM,
    /** Aggregatable numeric (nanos, ids, counts). */
    NUM,
    /** Timestamp: range-filterable; group through a tsX bucket instead. */
    TIME,
    /** LOB / long text: LIKE-filterable and displayable, never groupable (they are LONGTEXT). */
    TEXT
  }

  /** How a caller-supplied value is bound for this column. */
  public enum ValType {STRING, LONG, TIMESTAMP}

  public record ColumnDef(String key, String sql, Kind kind, ValType type, String label, String note) {

    public boolean groupable() {
      return kind == Kind.DIM;
    }

    public boolean aggregatable() {
      return kind == Kind.NUM;
    }
  }

  /**
   * How a logical measure is recovered from a rollup row. The raw tables store one duration per row;
   * the rollups store it pre-folded, so {@code sum:handlerDuration} becomes {@code SUM(sumHandlerNanos)}
   * and {@code max:handlerDuration} becomes {@code MAX(maxHandlerNanos)}. Percentiles come from the
   * 15-bucket histogram (merged by column-wise SUM, then interpolated in Java) and are therefore
   * approximate — which the result marks as {@code exact: false}.
   */
  public record MeasureDef(String key, String sumCol, String minCol, String maxCol, boolean histogram) {
  }

  public record TableDef(String key, String sqlTable, String timeColumn, String idColumn,
                         List<String> rowColumns, Map<String, ColumnDef> columns,
                         boolean rollup, String countExpr, Map<String, MeasureDef> measures) {

    /** Raw tables: one row per event, so counting is COUNT(*) and there are no pre-folded measures. */
    public TableDef(String key, String sqlTable, String timeColumn, String idColumn,
                    List<String> rowColumns, Map<String, ColumnDef> columns) {
      this(key, sqlTable, timeColumn, idColumn, rowColumns, columns, false, "COUNT(*)", Map.of());
    }

    public MeasureDef measure(String key) {
      MeasureDef m = measures.get(key);
      if (m == null) {
        throw new IllegalArgumentException("Column '" + key + "' is not an aggregatable measure on the "
            + this.key + " source. Available: " + String.join(", ", measures.keySet()));
      }
      return m;
    }

    /** Resolve a column or fail with a message naming the offender (becomes a 400). */
    public ColumnDef column(String key) {
      ColumnDef def = columns.get(key);
      if (def == null) {
        throw new IllegalArgumentException(
            "Unknown column '" + key + "' for table '" + this.key + "'. Known columns: " + String.join(", ", columns.keySet()));
      }
      return def;
    }

    public List<ColumnDef> byKind(Kind kind) {
      List<ColumnDef> out = new ArrayList<>();
      for (ColumnDef d : columns.values()) {
        if (d.kind() == kind) {
          out.add(d);
        }
      }
      return out;
    }
  }

  // ---- shared SQL fragments ------------------------------------------------------------------------

  private static final String HANDLER_SQL = "CONCAT(COALESCE(className,'?'),'.',COALESCE(methodName,'?'))";

  /**
   * Collapse 1,165 distinct paths into a readable set: UUIDs (bare or percent-encoded inside an
   * artifact @id) become {uuid}, long digit runs become {n}. MySQL 8 REGEXP_REPLACE.
   */
  private static final String PATH_TEMPLATE_SQL =
      "REGEXP_REPLACE(REGEXP_REPLACE(path,"
          + "'[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}','{uuid}'),"
          + "'[0-9]{4,}','{n}')";

  /**
   * status is NULL for every row before 2026-07-30 (added by the Phase-1 capture change), so
   * error-ness on history has to come from errorPack. 'unknown' vs 'err' keeps that honest instead of
   * silently reporting old traffic as successful.
   */
  private static final String STATUS_CLASS_SQL =
      "CASE WHEN status IS NULL THEN (CASE WHEN errorPack IS NULL THEN 'unknown' ELSE 'err' END) "
          + "WHEN status < 400 THEN 'ok' WHEN status < 500 THEN '4xx' ELSE '5xx' END";

  private static final Map<String, TableDef> TABLES = buildTables();

  private LogQueryColumns() {
  }

  public static TableDef table(String key) {
    TableDef def = TABLES.get(key);
    if (def == null) {
      throw new IllegalArgumentException(
          "Unknown table '" + key + "'. Expected one of: " + String.join(", ", TABLES.keySet()));
    }
    return def;
  }

  /**
   * Resolve (table, source) to a definition. The caller's vocabulary stays {@code request|cypher} plus
   * {@code raw|rollup}; which physical table that means is decided here, so the same spec can be run
   * against either source by flipping one field.
   */
  public static TableDef table(String key, String source) {
    String t = (key == null || key.isBlank()) ? T_REQUEST : key;
    String s = (source == null || source.isBlank()) ? SOURCE_RAW : source.toLowerCase(java.util.Locale.ROOT);
    if (SOURCE_ROLLUP.equals(s)) {
      return table(t + ROLLUP_SUFFIX);
    }
    if (!SOURCE_RAW.equals(s)) {
      throw new IllegalArgumentException("Unknown source '" + source + "'. Expected 'raw' or 'rollup'.");
    }
    return table(t);
  }

  public static List<String> tableKeys() {
    return List.copyOf(TABLES.keySet());
  }

  // ---- registry ----------------------------------------------------------------------------------

  private static Map<String, TableDef> buildTables() {
    Map<String, TableDef> tables = new LinkedHashMap<>();
    tables.put(T_REQUEST, requestTable());
    tables.put(T_CYPHER, cypherTable());
    tables.put(T_REQUEST + ROLLUP_SUFFIX, requestRollupTable());
    tables.put(T_CYPHER + ROLLUP_SUFFIX, cypherRollupTable());
    return tables;
  }

  /**
   * The hourly rollups: same query grammar, different source. They answer ranges beyond the raw
   * retention window at a fraction of the cost, with hourly grain and approximate percentiles.
   * Dimensions are limited to what was folded in — anything not in the rollup key is simply gone,
   * which is why the allowlist here is deliberately shorter than the raw one.
   */
  private static TableDef requestRollupTable() {
    Map<String, ColumnDef> c = new LinkedHashMap<>();
    dim(c, "component", "systemComponentName", "Component", null);
    dim(c, "className", "className", "Class", null);
    dim(c, "methodName", "methodName", "Method name", null);
    dim(c, "handler", HANDLER_SQL, "Handler", "derived: className.methodName");
    dim(c, "httpMethod", "httpMethod", "Method", null);
    dim(c, "statusClass", "statusClass", "Status class", "ok/4xx/5xx/err/unknown, folded at aggregation time");
    dim(c, "authSource", "authSource", "Auth source", null);
    rollupTimeBuckets(c);

    num(c, "handlerDuration", "sumHandlerNanos", "Handler duration", "nanos");
    num(c, "preHandlerDuration", "sumPreHandlerNanos", "Pre-handler duration", "nanos");
    num(c, "errorCount", "errorCount", "Errors", null);
    time(c, "hourUtc", "hourUtc", "Hour (UTC)");
    text(c, "samplePath", "samplePath", "Sample path", "one representative path per rollup key");

    Map<String, MeasureDef> m = new LinkedHashMap<>();
    m.put("handlerDuration", new MeasureDef("handlerDuration", "sumHandlerNanos", "minHandlerNanos",
        "maxHandlerNanos", true));
    m.put("preHandlerDuration", new MeasureDef("preHandlerDuration", "sumPreHandlerNanos", null, null, false));
    m.put("errorCount", new MeasureDef("errorCount", "errorCount", null, null, false));

    return new TableDef(T_REQUEST + ROLLUP_SUFFIX, "agg_request_hourly", "hourUtc", "id",
        List.of(), Map.copyOf(c), true, "SUM(reqCount)", Map.copyOf(m));
  }

  private static TableDef cypherRollupTable() {
    Map<String, ColumnDef> c = new LinkedHashMap<>();
    dim(c, "component", "systemComponentName", "Component", null);
    dim(c, "operation", "operation", "Operation", null);
    dim(c, "runnableHash", "runnableHash", "Query shape", "join agg_cypher_query_catalog for the text");
    rollupTimeBuckets(c);

    num(c, "duration", "sumNanos", "Duration", "nanos");
    time(c, "hourUtc", "hourUtc", "Hour (UTC)");

    Map<String, MeasureDef> m = new LinkedHashMap<>();
    m.put("duration", new MeasureDef("duration", "sumNanos", "minNanos", "maxNanos", true));

    return new TableDef(T_CYPHER + ROLLUP_SUFFIX, "agg_cypher_hourly", "hourUtc", "id",
        List.of(), Map.copyOf(c), true, "SUM(execCount)", Map.copyOf(m));
  }

  /** Rollup grain is hourly, so there is no tsMinute — asking for one is rejected by name. */
  private static void rollupTimeBuckets(Map<String, ColumnDef> c) {
    dim(c, "tsHour", "hourUtc", "Hour", "rollup grain");
    dim(c, "tsDay", "DATE(hourUtc)", "Day", "derived bucket");
    dim(c, "hourOfDay", "HOUR(hourUtc)", "Hour of day", "derived: 0-23, for off-hours patterns");
    dim(c, "dayOfWeek", "DAYOFWEEK(hourUtc)", "Day of week", "derived: 1=Sunday");
  }

  private static TableDef requestTable() {
    Map<String, ColumnDef> c = new LinkedHashMap<>();

    // dimensions — all indexed except the derived expressions (bounded by the mandatory time range)
    dim(c, "component", "systemComponentName", "Component", null);
    dim(c, "httpMethod", "httpMethod", "Method", null);
    dim(c, "authSource", "authSource", "Auth source", null);
    dim(c, "className", "className", "Class", null);
    dim(c, "methodName", "methodName", "Method name", null);
    dim(c, "handler", HANDLER_SQL, "Handler", "derived: className.methodName");
    dim(c, "userId", "userId", "User", null);
    dim(c, "apiKeyHash", "apiKeyHash", "API key", "populated from 2026-07-30 only");
    dim(c, "clientSessionId", "clientSessionId", "Session", null);
    dim(c, "globalRequestId", "globalRequestId", "Global request id", null);
    dim(c, "localRequestId", "localRequestId", "Local request id", null);
    dim(c, "globalRequestIdSource", "globalRequestIdSource", "Request id source", null);
    dim(c, "type", "type", "Type", "single-valued today — not a useful facet");
    dim(c, "subType", "subType", "Sub type", "single-valued today — not a useful facet");
    numDim(c, "status", "status", "HTTP status", "populated from 2026-07-30 only");
    dim(c, "statusClass", STATUS_CLASS_SQL, "Status class", "derived: ok/4xx/5xx/err/unknown");
    dim(c, "pathTemplate", PATH_TEMPLATE_SQL, "Path template", "derived: ids replaced by {uuid}/{n}");
    timeBuckets(c, "requestTime");

    // numerics (nanos)
    num(c, "handlerDuration", "handlerDuration", "Handler duration", "nanos");
    num(c, "preHandlerDuration", "preHandlerDuration", "Pre-handler duration", "nanos");

    // timestamps
    time(c, "requestTime", "requestTime", "Request time");
    time(c, "startTime", "startTime", "Start time");
    time(c, "endTime", "endTime", "End time");
    time(c, "aggregatedAt", "aggregatedAt", "Aggregated at");

    // text / LOB
    text(c, "path", "path", "Path", "not indexed — prefer pathTemplate for grouping");
    text(c, "queryParameters", "queryParameters", "Query parameters", null);
    text(c, "errorPack", "errorPack", "Error pack", "LONGTEXT");

    List<String> rowColumns = List.of("requestTime", "component", "httpMethod", "path", "handler",
        "userId", "authSource", "apiKeyHash", "status", "handlerDuration", "globalRequestId", "errorPack");

    return new TableDef(T_REQUEST, "log_request", "requestTime", "id", rowColumns, Map.copyOf(c));
  }

  private static TableDef cypherTable() {
    Map<String, ColumnDef> c = new LinkedHashMap<>();

    dim(c, "component", "systemComponentName", "Component", null);
    dim(c, "operation", "operation", "Operation", null);
    dim(c, "runnableHash", "runnableHash", "Query shape", "md5 of the runnable text");
    dim(c, "parametersHash", "parametersHash", "Parameters hash", null);
    dim(c, "className", "className", "Class", null);
    dim(c, "methodName", "methodName", "Method name", null);
    dim(c, "handler", HANDLER_SQL, "Handler", "derived: className.methodName");
    dim(c, "globalRequestId", "globalRequestId", "Global request id", "joins to log_request");
    dim(c, "localRequestId", "localRequestId", "Local request id", null);
    timeBuckets(c, "logTime");

    num(c, "duration", "duration", "Duration", "nanos");

    time(c, "logTime", "logTime", "Log time");
    time(c, "startTime", "startTime", "Start time");
    time(c, "endTime", "endTime", "End time");
    time(c, "aggregatedAt", "aggregatedAt", "Aggregated at");

    text(c, "runnable", "runnable", "Runnable query", "LONGTEXT");
    text(c, "original", "original", "Original query", "LONGTEXT");
    text(c, "interpolated", "interpolated", "Interpolated query", "LONGTEXT");
    text(c, "parameters", "parameters", "Parameters", "LONGTEXT");

    List<String> rowColumns = List.of("logTime", "component", "operation", "runnableHash", "handler",
        "duration", "globalRequestId", "runnable", "parameters");

    return new TableDef(T_CYPHER, "log_cypher", "logTime", "id", rowColumns, Map.copyOf(c));
  }

  /**
   * Time-bucket dims for charting: group by minute/hour/day, or fold every day onto hour-of-day.
   * <p>
   * Truncation is done with date arithmetic rather than {@code DATE_FORMAT('%Y-%m-%d %H:%i:00')} on
   * purpose: these expressions are embedded in Hibernate <em>native</em> queries, and Hibernate scans
   * the SQL text for {@code :name} bind parameters before MySQL ever sees it — a colon inside a format
   * string gets mistaken for a parameter and the query dies with "Named parameter not bound".
   * Backticks do not protect it. Keep every expression here colon-free.
   */
  private static void timeBuckets(Map<String, ColumnDef> c, String timeCol) {
    String hourTrunc = "DATE_ADD(DATE(" + timeCol + "), INTERVAL HOUR(" + timeCol + ") HOUR)";
    dim(c, "tsMinute", "DATE_ADD(" + hourTrunc + ", INTERVAL MINUTE(" + timeCol + ") MINUTE)", "Minute",
        "derived bucket");
    dim(c, "tsHour", hourTrunc, "Hour", "derived bucket");
    dim(c, "tsDay", "DATE(" + timeCol + ")", "Day", "derived bucket");
    dim(c, "hourOfDay", "HOUR(" + timeCol + ")", "Hour of day", "derived: 0-23, for off-hours patterns");
    dim(c, "dayOfWeek", "DAYOFWEEK(" + timeCol + ")", "Day of week", "derived: 1=Sunday");
  }

  private static void dim(Map<String, ColumnDef> c, String key, String sql, String label, String note) {
    c.put(key, new ColumnDef(key, sql, Kind.DIM, ValType.STRING, label, note));
  }

  /** A dimension whose values bind as numbers (status), so index use isn't lost to string coercion. */
  private static void numDim(Map<String, ColumnDef> c, String key, String sql, String label, String note) {
    c.put(key, new ColumnDef(key, sql, Kind.DIM, ValType.LONG, label, note));
  }

  private static void num(Map<String, ColumnDef> c, String key, String sql, String label, String note) {
    c.put(key, new ColumnDef(key, sql, Kind.NUM, ValType.LONG, label, note));
  }

  private static void time(Map<String, ColumnDef> c, String key, String sql, String label) {
    c.put(key, new ColumnDef(key, sql, Kind.TIME, ValType.TIMESTAMP, label, null));
  }

  private static void text(Map<String, ColumnDef> c, String key, String sql, String label, String note) {
    c.put(key, new ColumnDef(key, sql, Kind.TEXT, ValType.STRING, label, note));
  }
}
