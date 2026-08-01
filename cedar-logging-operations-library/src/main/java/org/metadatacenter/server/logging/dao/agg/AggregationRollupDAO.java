package org.metadatacenter.server.logging.dao.agg;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.metadatacenter.server.logging.agg.LatencyHistogram;
import org.metadatacenter.server.logging.agg.RollupAccumulators;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestHourly;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Owns all native SQL for the rollups: reading the frozen {@code *_pre284} tables (projecting the
 * columns that the aggregator needs, supplying constants for the ones history never captured) and the
 * additive upserts into the {@code agg_*} tables. Extends {@link AbstractDAO} only to get
 * {@code currentSession()}; the parameter type {@link AggRequestHourly} is incidental.
 * <p>
 * All methods assume an active session/transaction (the caller is {@code @UnitOfWork}).
 */
public class AggregationRollupDAO extends AbstractDAO<AggRequestHourly> {

  private static final int NB = LatencyHistogram.BUCKETS;

  private static final String REQUEST_UPSERT = buildRequestUpsert();
  private static final String CYPHER_UPSERT = buildCypherUpsert();
  private static final String USER_UPSERT =
      "INSERT INTO agg_request_user_hourly (hourUtc, userId, authSource, apiKeyHash, reqCount, errorCount, sumHandlerNanos) "
          + "VALUES (:hourUtc, :userId, :authSource, :apiKeyHash, :reqCount, :errorCount, :sumHandler) "
          + "ON DUPLICATE KEY UPDATE reqCount = reqCount + VALUES(reqCount), "
          + "errorCount = errorCount + VALUES(errorCount), sumHandlerNanos = sumHandlerNanos + VALUES(sumHandlerNanos)";
  private static final String CATALOG_UPSERT =
      "INSERT INTO agg_cypher_query_catalog (runnableHash, operation, runnableSample, interpolatedSample, sampleClassName, sampleMethodName, firstSeen, lastSeen) "
          + "VALUES (:hash, :op, :runnable, :interp, :cn, :mn, :firstSeen, :lastSeen) "
          + "ON DUPLICATE KEY UPDATE firstSeen = LEAST(firstSeen, VALUES(firstSeen)), lastSeen = GREATEST(lastSeen, VALUES(lastSeen))";

  public AggregationRollupDAO(SessionFactory factory) {
    super(factory);
  }

  // ---- SQL builders (generate h0..h14 so we never hand-type 15 columns) --------------------------

  private static String buildRequestUpsert() {
    StringBuilder cols = new StringBuilder(
        "hourUtc, systemComponentName, className, methodName, httpMethod, statusClass, authSource, "
            + "reqCount, errorCount, sumHandlerNanos, minHandlerNanos, maxHandlerNanos, sumPreHandlerNanos");
    StringBuilder vals = new StringBuilder(
        ":hourUtc, :component, :className, :methodName, :httpMethod, :statusClass, :authSource, "
            + ":reqCount, :errorCount, :sumHandler, :minHandler, :maxHandler, :sumPre");
    for (int i = 0; i < NB; i++) {
      cols.append(", h").append(i);
      vals.append(", :h").append(i);
    }
    cols.append(", samplePath");
    vals.append(", :samplePath");

    StringBuilder upd = new StringBuilder(
        "reqCount = reqCount + VALUES(reqCount), errorCount = errorCount + VALUES(errorCount), "
            + "sumHandlerNanos = sumHandlerNanos + VALUES(sumHandlerNanos), "
            // treat 0 as "unset" so a duration-less batch never poisons the running minimum
            + "minHandlerNanos = IF(minHandlerNanos = 0, VALUES(minHandlerNanos), "
            + "IF(VALUES(minHandlerNanos) = 0, minHandlerNanos, LEAST(minHandlerNanos, VALUES(minHandlerNanos)))), "
            + "maxHandlerNanos = GREATEST(maxHandlerNanos, VALUES(maxHandlerNanos)), "
            + "sumPreHandlerNanos = sumPreHandlerNanos + VALUES(sumPreHandlerNanos)");
    for (int i = 0; i < NB; i++) {
      upd.append(", h").append(i).append(" = h").append(i).append(" + VALUES(h").append(i).append(")");
    }
    // samplePath deliberately not updated on conflict — keep the first representative
    return "INSERT INTO agg_request_hourly (" + cols + ") VALUES (" + vals + ") ON DUPLICATE KEY UPDATE " + upd;
  }

  private static String buildCypherUpsert() {
    StringBuilder cols = new StringBuilder(
        "hourUtc, systemComponentName, operation, runnableHash, execCount, sumNanos, minNanos, maxNanos");
    StringBuilder vals = new StringBuilder(
        ":hourUtc, :component, :operation, :runnableHash, :execCount, :sumNanos, :minNanos, :maxNanos");
    for (int i = 0; i < NB; i++) {
      cols.append(", h").append(i);
      vals.append(", :h").append(i);
    }
    StringBuilder upd = new StringBuilder(
        "execCount = execCount + VALUES(execCount), sumNanos = sumNanos + VALUES(sumNanos), "
            + "minNanos = IF(minNanos = 0, VALUES(minNanos), "
            + "IF(VALUES(minNanos) = 0, minNanos, LEAST(minNanos, VALUES(minNanos)))), "
            + "maxNanos = GREATEST(maxNanos, VALUES(maxNanos))");
    for (int i = 0; i < NB; i++) {
      upd.append(", h").append(i).append(" = h").append(i).append(" + VALUES(h").append(i).append(")");
    }
    return "INSERT INTO agg_cypher_hourly (" + cols + ") VALUES (" + vals + ") ON DUPLICATE KEY UPDATE " + upd;
  }

  // ---- reading the frozen history ----------------------------------------------------------------
  // NOTE: verify the *_pre284 column set with SHOW CREATE TABLE before the first real run. These
  // projections use the long-standing columns; if the frozen (pre-2.8.4) schema lacks one (e.g.
  // preHandlerDuration), adjust the SELECT — a dry run on a tiny id-range surfaces any mismatch.

  private static final String SELECT_HIST_REQUEST =
      "SELECT id, systemComponentName, className, methodName, httpMethod, userId, authSource, "
          + "requestTime, handlerDuration, preHandlerDuration, path, (errorPack IS NOT NULL) AS hasError "
          + "FROM log_request_pre284 WHERE id > :fromId ORDER BY id LIMIT :lim";

  private static final String SELECT_HIST_CYPHER =
      "SELECT id, systemComponentName, operation, runnableHash, duration, logTime, runnable, interpolated, "
          + "className, methodName FROM log_cypher_pre284 WHERE id > :fromId ORDER BY id LIMIT :lim";

  // Live path: reads the current tables (which DO have status + apiKeyHash), a settled UTC day at a
  // time, in id order, taking only rows not yet marked. aggregatedAt IS the progress cursor — the
  // batch's mark UPDATE commits in the same transaction as the upsert, so a crash leaves the rows
  // unmarked (and the deltas rolled back) for clean reprocessing.
  private static final String SELECT_LIVE_REQUEST =
      "SELECT id, systemComponentName, className, methodName, httpMethod, userId, authSource, apiKeyHash, "
          + "status, requestTime, handlerDuration, preHandlerDuration, path, (errorPack IS NOT NULL) AS hasError "
          + "FROM log_request WHERE aggregatedAt IS NULL AND requestTime >= :ds AND requestTime < :de "
          + "ORDER BY id LIMIT :lim";
  private static final String MARK_LIVE_REQUEST =
      "UPDATE log_request SET aggregatedAt = :now WHERE aggregatedAt IS NULL "
          + "AND requestTime >= :ds AND requestTime < :de AND id <= :maxId";
  private static final String SELECT_LIVE_CYPHER =
      "SELECT id, systemComponentName, operation, runnableHash, duration, logTime, runnable, interpolated, "
          + "className, methodName FROM log_cypher WHERE aggregatedAt IS NULL AND logTime >= :ds AND logTime < :de "
          + "ORDER BY id LIMIT :lim";
  private static final String MARK_LIVE_CYPHER =
      "UPDATE log_cypher SET aggregatedAt = :now WHERE aggregatedAt IS NULL "
          + "AND logTime >= :ds AND logTime < :de AND id <= :maxId";
  private static final String PRUNE_REQUEST =
      "DELETE FROM log_request WHERE aggregatedAt IS NOT NULL AND requestTime < :cutoff ORDER BY id LIMIT :lim";
  private static final String PRUNE_CYPHER =
      "DELETE FROM log_cypher WHERE aggregatedAt IS NOT NULL AND logTime < :cutoff ORDER BY id LIMIT :lim";

  /**
   * Read a batch of frozen request rows and fold them into {@code acc}.
   *
   * @return {@code {rowsRead, maxIdInBatch}}; {@code maxIdInBatch == fromId} when nothing was read.
   */
  @SuppressWarnings("unchecked")
  public long[] foldHistoricalRequestBatch(RollupAccumulators acc, long fromId, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_HIST_REQUEST)
        .setParameter("fromId", fromId).setParameter("lim", limit).getResultList();
    long maxId = fromId;
    for (Object[] r : rows) {
      long id = num(r[0]);
      maxId = Math.max(maxId, id);
      String component = str(r[1]);
      String className = str(r[2]);
      String methodName = str(r[3]);
      String httpMethod = str(r[4]);
      String userId = str(r[5]);
      String authSource = str(r[6]);
      Instant when = ts(r[7]);
      long handlerNanos = num(r[8]);
      long preNanos = num(r[9]);
      String path = str(r[10]);
      boolean hasError = num(r[11]) != 0;
      if (when == null) {
        continue; // no timestamp -> cannot place in an hour bucket; skip (counted as skipped upstream)
      }
      // history has no HTTP status: error vs unknown is all we can derive
      String statusClass = hasError ? "err" : "unknown";
      acc.foldRequest(when, component, className, methodName, httpMethod, statusClass, authSource,
          userId, null, handlerNanos, preNanos, hasError, path);
    }
    return new long[]{rows.size(), maxId};
  }

  @SuppressWarnings("unchecked")
  public long[] foldHistoricalCypherBatch(RollupAccumulators acc, long fromId, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_HIST_CYPHER)
        .setParameter("fromId", fromId).setParameter("lim", limit).getResultList();
    long maxId = fromId;
    for (Object[] r : rows) {
      long id = num(r[0]);
      maxId = Math.max(maxId, id);
      String component = str(r[1]);
      String operation = str(r[2]);
      String runnableHash = str(r[3]);
      long nanos = num(r[4]);
      Instant when = ts(r[5]);
      String runnable = str(r[6]);
      String interpolated = str(r[7]);
      String className = str(r[8]);
      String methodName = str(r[9]);
      if (when == null) {
        continue;
      }
      acc.foldCypher(when, component, operation, runnableHash, nanos, runnable, interpolated,
          className, methodName);
    }
    return new long[]{rows.size(), maxId};
  }

  // ---- live path (current tables, per settled UTC day) ------------------------------------------

  /** @return {rowsRead, maxIdInBatch}; rowsRead 0 means the day is fully aggregated. */
  @SuppressWarnings("unchecked")
  public long[] foldLiveRequestBatch(RollupAccumulators acc, Instant dayStart, Instant dayEnd, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_LIVE_REQUEST)
        .setParameter("ds", Timestamp.from(dayStart))
        .setParameter("de", Timestamp.from(dayEnd))
        .setParameter("lim", limit).getResultList();
    long maxId = 0;
    for (Object[] r : rows) {
      maxId = Math.max(maxId, num(r[0]));
      String component = str(r[1]);
      String className = str(r[2]);
      String methodName = str(r[3]);
      String httpMethod = str(r[4]);
      String userId = str(r[5]);
      String authSource = str(r[6]);
      String apiKeyHash = str(r[7]);
      Integer status = intOrNull(r[8]);
      Instant when = ts(r[9]);
      long handlerNanos = num(r[10]);
      long preNanos = num(r[11]);
      String path = str(r[12]);
      boolean hasError = num(r[13]) != 0;
      if (when == null) {
        continue;
      }
      boolean isError = status != null ? status >= 400 : hasError;
      acc.foldRequest(when, component, className, methodName, httpMethod, statusClass(status, hasError),
          authSource, userId, apiKeyHash, handlerNanos, preNanos, isError, path);
    }
    return new long[]{rows.size(), maxId};
  }

  @SuppressWarnings("unchecked")
  public long[] foldLiveCypherBatch(RollupAccumulators acc, Instant dayStart, Instant dayEnd, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_LIVE_CYPHER)
        .setParameter("ds", Timestamp.from(dayStart))
        .setParameter("de", Timestamp.from(dayEnd))
        .setParameter("lim", limit).getResultList();
    long maxId = 0;
    for (Object[] r : rows) {
      maxId = Math.max(maxId, num(r[0]));
      String component = str(r[1]);
      String operation = str(r[2]);
      String runnableHash = str(r[3]);
      long nanos = num(r[4]);
      Instant when = ts(r[5]);
      String runnable = str(r[6]);
      String interpolated = str(r[7]);
      String className = str(r[8]);
      String methodName = str(r[9]);
      if (when == null) {
        continue;
      }
      acc.foldCypher(when, component, operation, runnableHash, nanos, runnable, interpolated,
          className, methodName);
    }
    return new long[]{rows.size(), maxId};
  }

  /** Mark exactly the rows just folded (the smallest-id unmarked rows in the day, up to maxId). */
  public int markLiveRequestRows(Instant dayStart, Instant dayEnd, long maxId, Instant now) {
    return currentSession().createNativeMutationQuery(MARK_LIVE_REQUEST)
        .setParameter("now", Timestamp.from(now))
        .setParameter("ds", Timestamp.from(dayStart))
        .setParameter("de", Timestamp.from(dayEnd))
        .setParameter("maxId", maxId)
        .executeUpdate();
  }

  public int markLiveCypherRows(Instant dayStart, Instant dayEnd, long maxId, Instant now) {
    return currentSession().createNativeMutationQuery(MARK_LIVE_CYPHER)
        .setParameter("now", Timestamp.from(now))
        .setParameter("ds", Timestamp.from(dayStart))
        .setParameter("de", Timestamp.from(dayEnd))
        .setParameter("maxId", maxId)
        .executeUpdate();
  }

  /** Earliest not-yet-aggregated timestamp in a live table, or null if all are aggregated. */
  public Instant earliestUnaggregated(String timeColumn, String table) {
    Object o = currentSession()
        .createNativeQuery("SELECT MIN(" + safeCol(timeColumn) + ") FROM " + safe(table)
            + " WHERE aggregatedAt IS NULL")
        .getSingleResult();
    return ts(o);
  }

  public int pruneRequests(Instant cutoff, int limit) {
    return currentSession().createNativeMutationQuery(PRUNE_REQUEST)
        .setParameter("cutoff", Timestamp.from(cutoff)).setParameter("lim", limit).executeUpdate();
  }

  public int pruneCypher(Instant cutoff, int limit) {
    return currentSession().createNativeMutationQuery(PRUNE_CYPHER)
        .setParameter("cutoff", Timestamp.from(cutoff)).setParameter("lim", limit).executeUpdate();
  }

  // ---- outlier retention (top-N slow / error INSTANCES, kept forever) ----------------------------
  // Server-side INSERT ... SELECT so only the N chosen rows' LOBs move. Ordered by the indexed
  // duration column. The row's own timestamp is kept; no dayUtc needed.

  private static final String REQ_OUT_COLS =
      "(requestTime, systemComponentName, httpMethod, path, className, methodName, userId, authSource, "
          + "apiKeyHash, status, durationNanos, kind, errorPack)";
  private static final String CYP_OUT_COLS =
      "(logTime, systemComponentName, operation, runnableHash, durationNanos, runnable, interpolated, "
          + "parameters, className, methodName)";

  /** Live per-day capture over log_request (has status + apiKeyHash). Idempotent: clears the day first. */
  public void captureLiveRequestOutliers(Instant from, Instant to, int topSlow, int topErrors) {
    Timestamp ds = Timestamp.from(from), de = Timestamp.from(to);
    currentSession().createNativeMutationQuery(
            "DELETE FROM agg_request_outlier WHERE requestTime >= :ds AND requestTime < :de")
        .setParameter("ds", ds).setParameter("de", de).executeUpdate();
    currentSession().createNativeMutationQuery("INSERT INTO agg_request_outlier " + REQ_OUT_COLS
            + " SELECT requestTime, systemComponentName, httpMethod, path, className, methodName, userId, "
            + "authSource, apiKeyHash, status, handlerDuration, 'SLOW', errorPack FROM log_request "
            + "WHERE requestTime >= :ds AND requestTime < :de ORDER BY handlerDuration DESC LIMIT :n")
        .setParameter("ds", ds).setParameter("de", de).setParameter("n", topSlow).executeUpdate();
    currentSession().createNativeMutationQuery("INSERT INTO agg_request_outlier " + REQ_OUT_COLS
            + " SELECT requestTime, systemComponentName, httpMethod, path, className, methodName, userId, "
            + "authSource, apiKeyHash, status, handlerDuration, 'ERROR', errorPack FROM log_request "
            + "WHERE requestTime >= :ds AND requestTime < :de AND errorPack IS NOT NULL "
            + "ORDER BY handlerDuration DESC LIMIT :n")
        .setParameter("ds", ds).setParameter("de", de).setParameter("n", topErrors).executeUpdate();
  }

  /** One-shot capture over log_request_pre284 (no status/apiKeyHash → NULL). */
  public void captureHistoryRequestOutliers(int topSlow, int topErrors) {
    currentSession().createNativeMutationQuery("INSERT INTO agg_request_outlier " + REQ_OUT_COLS
            + " SELECT requestTime, systemComponentName, httpMethod, path, className, methodName, userId, "
            + "authSource, NULL, NULL, handlerDuration, 'SLOW', errorPack FROM log_request_pre284 "
            + "ORDER BY handlerDuration DESC LIMIT :n")
        .setParameter("n", topSlow).executeUpdate();
    currentSession().createNativeMutationQuery("INSERT INTO agg_request_outlier " + REQ_OUT_COLS
            + " SELECT requestTime, systemComponentName, httpMethod, path, className, methodName, userId, "
            + "authSource, NULL, NULL, handlerDuration, 'ERROR', errorPack FROM log_request_pre284 "
            + "WHERE errorPack IS NOT NULL ORDER BY handlerDuration DESC LIMIT :n")
        .setParameter("n", topErrors).executeUpdate();
  }

  /** Cypher outliers; {@code from}/{@code to} null = whole table (used for *_pre284). */
  public void captureCypherOutliers(String table, Instant from, Instant to, int topSlow) {
    boolean bounded = from != null && to != null;
    if (bounded) {
      currentSession().createNativeMutationQuery(
              "DELETE FROM agg_cypher_outlier WHERE logTime >= :ds AND logTime < :de")
          .setParameter("ds", Timestamp.from(from)).setParameter("de", Timestamp.from(to)).executeUpdate();
    }
    String where = bounded ? " WHERE logTime >= :ds AND logTime < :de" : "";
    var q = currentSession().createNativeMutationQuery("INSERT INTO agg_cypher_outlier " + CYP_OUT_COLS
        + " SELECT logTime, systemComponentName, operation, runnableHash, duration, runnable, interpolated, "
        + "parameters, className, methodName FROM " + safe(table) + where + " ORDER BY duration DESC LIMIT :n");
    if (bounded) {
      q.setParameter("ds", Timestamp.from(from)).setParameter("de", Timestamp.from(to));
    }
    q.setParameter("n", topSlow).executeUpdate();
  }

  private static String statusClass(Integer status, boolean hasError) {
    if (status == null) {
      return hasError ? "err" : "unknown";
    }
    return switch (status / 100) {
      case 2 -> "2xx";
      case 3 -> "3xx";
      case 4 -> "4xx";
      case 5 -> "5xx";
      default -> "unknown";
    };
  }

  private static Integer intOrNull(Object o) {
    return o == null ? null : ((Number) o).intValue();
  }

  public long maxId(String table) {
    Object o = currentSession().createNativeQuery("SELECT COALESCE(MAX(id), 0) FROM " + safe(table))
        .getSingleResult();
    return num(o);
  }

  public long count(String table) {
    Object o = currentSession().createNativeQuery("SELECT COUNT(*) FROM " + safe(table)).getSingleResult();
    return num(o);
  }

  // ---- flushing the accumulators (additive upserts) ----------------------------------------------

  public void flush(RollupAccumulators acc) {
    for (Map.Entry<RollupAccumulators.ReqKey, RollupAccumulators.ReqAgg> e : acc.requests.entrySet()) {
      upsertRequest(e.getKey(), e.getValue());
    }
    for (Map.Entry<RollupAccumulators.UserKey, RollupAccumulators.UserAgg> e : acc.users.entrySet()) {
      upsertUser(e.getKey(), e.getValue());
    }
    for (Map.Entry<RollupAccumulators.CypherKey, RollupAccumulators.CypherAgg> e : acc.cyphers.entrySet()) {
      upsertCypher(e.getKey(), e.getValue());
    }
    for (Map.Entry<String, RollupAccumulators.CatalogEntry> e : acc.catalog.entrySet()) {
      upsertCatalog(e.getKey(), e.getValue());
    }
  }

  private void upsertRequest(RollupAccumulators.ReqKey k, RollupAccumulators.ReqAgg a) {
    var q = currentSession().createNativeMutationQuery(REQUEST_UPSERT)
        .setParameter("hourUtc", Timestamp.from(k.hourUtc()))
        .setParameter("component", k.component())
        .setParameter("className", k.className())
        .setParameter("methodName", k.methodName())
        .setParameter("httpMethod", k.httpMethod())
        .setParameter("statusClass", k.statusClass())
        .setParameter("authSource", k.authSource())
        .setParameter("reqCount", a.reqCount)
        .setParameter("errorCount", a.errorCount)
        .setParameter("sumHandler", a.sumHandlerNanos)
        .setParameter("minHandler", a.maxHandlerNanos == Long.MIN_VALUE ? 0L : a.minHandlerNanos)
        .setParameter("maxHandler", a.maxHandlerNanos == Long.MIN_VALUE ? 0L : a.maxHandlerNanos)
        .setParameter("sumPre", a.sumPreHandlerNanos)
        .setParameter("samplePath", a.samplePath);
    for (int i = 0; i < NB; i++) {
      q.setParameter("h" + i, a.hist[i]);
    }
    q.executeUpdate();
  }

  private void upsertUser(RollupAccumulators.UserKey k, RollupAccumulators.UserAgg a) {
    currentSession().createNativeMutationQuery(USER_UPSERT)
        .setParameter("hourUtc", Timestamp.from(k.hourUtc()))
        .setParameter("userId", k.userId())
        .setParameter("authSource", k.authSource())
        .setParameter("apiKeyHash", k.apiKeyHash())
        .setParameter("reqCount", a.reqCount)
        .setParameter("errorCount", a.errorCount)
        .setParameter("sumHandler", a.sumHandlerNanos)
        .executeUpdate();
  }

  private void upsertCypher(RollupAccumulators.CypherKey k, RollupAccumulators.CypherAgg a) {
    var q = currentSession().createNativeMutationQuery(CYPHER_UPSERT)
        .setParameter("hourUtc", Timestamp.from(k.hourUtc()))
        .setParameter("component", k.component())
        .setParameter("operation", k.operation())
        .setParameter("runnableHash", k.runnableHash())
        .setParameter("execCount", a.execCount)
        .setParameter("sumNanos", a.sumNanos)
        .setParameter("minNanos", a.maxNanos == Long.MIN_VALUE ? 0L : a.minNanos)
        .setParameter("maxNanos", a.maxNanos == Long.MIN_VALUE ? 0L : a.maxNanos);
    for (int i = 0; i < NB; i++) {
      q.setParameter("h" + i, a.hist[i]);
    }
    q.executeUpdate();
  }

  private void upsertCatalog(String hash, RollupAccumulators.CatalogEntry c) {
    currentSession().createNativeMutationQuery(CATALOG_UPSERT)
        .setParameter("hash", hash)
        .setParameter("op", c.operation)
        .setParameter("runnable", c.runnableSample)
        .setParameter("interp", c.interpolatedSample)
        .setParameter("cn", c.className)
        .setParameter("mn", c.methodName)
        .setParameter("firstSeen", c.firstSeen == null ? null : Timestamp.from(c.firstSeen))
        .setParameter("lastSeen", c.lastSeen == null ? null : Timestamp.from(c.lastSeen))
        .executeUpdate();
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private static long num(Object o) {
    return o == null ? 0L : ((Number) o).longValue();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static Instant ts(Object o) {
    if (o == null) {
      return null;
    }
    if (o instanceof Timestamp t) {
      return t.toInstant();
    }
    if (o instanceof Instant i) {
      return i;
    }
    return null;
  }

  /** Guard the table name (only our four known tables are ever passed). */
  private static String safe(String table) {
    if (!table.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("Illegal table name: " + table);
    }
    return table;
  }

  /** Guard a column name (only our own literals are ever passed). */
  private static String safeCol(String col) {
    if (!col.matches("[A-Za-z0-9_]+")) {
      throw new IllegalArgumentException("Illegal column name: " + col);
    }
    return col;
  }
}
