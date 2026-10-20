package org.metadatacenter.server.logging.dao.agg;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.metadatacenter.server.logging.agg.LatencyHistogram;
import org.metadatacenter.server.logging.agg.RollupAccumulators;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestHourly;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Owns all native SQL for the rollups: reading the frozen {@code *_pre284} tables (projecting the
 * columns that the aggregator needs, supplying constants for the ones history never captured) and the
 * additive upserts into the {@code agg_*} tables. Extends {@link AbstractDAO} only to get
 * {@code currentSession()}; the parameter type {@link AggRequestHourly} is incidental.
 * <p>
 * All methods assume an active session/transaction (the caller is {@code @UnitOfWork}).
 * <p>
 * Times cross this DAO as text, never as {@code java.sql.Timestamp}. The log tables' DATETIME columns hold UTC
 * wall-clock (the entities bind their {@code Instant}s in UTC), but a native read or a bound {@code Timestamp} is
 * converted through the connection's {@code serverTimezone} (America/Los_Angeles). That was self-consistent, yet it
 * made each "UTC day" of the live aggregator run 17:00 to 17:00 UTC (16:00 in winter), and a stored value inside
 * the spring-forward gap (2027-03-14 02:00-02:59) does not exist in that zone, so reading it throws
 * {@code HOUR_OF_DAY: 2 -> 3} and would stop the aggregator for good. Reading {@link #asText} and binding
 * {@link #sqlTime} applies no zone at all. The rollups' stored hours do not change: the old round trip shifted each
 * time by the offset on the way in and back on the way out.
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
          + asText("requestTime") + ", handlerDuration, preHandlerDuration, path, (errorPack IS NOT NULL) AS hasError "
          + "FROM log_request_pre284 WHERE id > :fromId ORDER BY id LIMIT :lim";

  private static final String SELECT_HIST_CYPHER =
      "SELECT id, systemComponentName, operation, runnableHash, duration, " + asText("logTime") + ", runnable, "
          + "interpolated, className, methodName FROM log_cypher_pre284 WHERE id > :fromId ORDER BY id LIMIT :lim";

  // Only completed requests are immutable enough to aggregate. Missing fragments remain in raw logs until they
  // arrive; a time margin cannot prove that a durable queue has drained. A request is complete once both filters
  // have logged it: the pre-matching request filter (requestTime) and the response filter (endTime, status), which
  // runs after the handler for every response. The handler's own START fragment (startTime, className, userId) is
  // logged only when the resource builds a request context, so a 404, a rejected token or any resource that never
  // builds one has no startTime, ever. Requiring it left those rows unaggregated and unpruned for good. Nor is
  // status required: the response filter leaves it null when it has none, and statusClass() reads null as unknown.
  private static final String COMPLETE_REQUEST = "requestTime IS NOT NULL AND endTime IS NOT NULL";

  // A live batch is read in two steps. The first finds the batch's ids in the order of the composite
  // (aggregatedAt, time) index, so it reads index entries only up to the LIMIT: no sort, and only the batch's rows
  // are locked. ORDER BY id instead made MariaDB filesort every remaining row of the day per batch, and for
  // log_cypher, whose TEXT columns came along, that was a ~7 GB read per batch. The second step reads the locked
  // rows by primary key. The batch is marked by its exact ids, so the order the ids come in does not matter.
  private static final String SELECT_LIVE_REQUEST_IDS =
      "SELECT id FROM log_request WHERE aggregatedAt IS NULL AND " + COMPLETE_REQUEST
          + " AND requestTime >= :ds AND requestTime < :de ORDER BY requestTime, id LIMIT :lim FOR UPDATE SKIP LOCKED";
  private static final String SELECT_LIVE_REQUEST_ROWS =
      "SELECT id, systemComponentName, className, methodName, httpMethod, userId, authSource, apiKeyHash, "
          + "status, " + asText("requestTime") + ", handlerDuration, preHandlerDuration, path, "
          + "(errorPack IS NOT NULL) AS hasError FROM log_request WHERE id IN (:ids)";
  private static final String SELECT_LIVE_CYPHER_IDS =
      "SELECT id FROM log_cypher WHERE aggregatedAt IS NULL AND logTime >= :ds AND logTime < :de "
          + "ORDER BY logTime, id LIMIT :lim FOR UPDATE SKIP LOCKED";
  private static final String SELECT_LIVE_CYPHER_ROWS =
      "SELECT id, systemComponentName, operation, runnableHash, duration, " + asText("logTime") + ", runnable, "
          + "interpolated, className, methodName FROM log_cypher WHERE id IN (:ids)";

  // The prune deletes one primary-key window per statement. Its other conditions are written as expressions so that
  // the optimizer can use no index but PRIMARY: given a plain "aggregatedAt IS NOT NULL" it chose that index and
  // locked ~10.4M rows for a 1000-row DELETE. Only rows that are aggregated AND older than the cutoff are deleted.
  private static final String PRUNE_AGGREGATED = "COALESCE(aggregatedAt, '1970-01-01') > '1970-01-01'";
  private static final String PRUNE_REQUEST =
      "DELETE FROM log_request WHERE id >= :lo AND id < :hi AND " + PRUNE_AGGREGATED
          + " AND (requestTime + INTERVAL 0 SECOND) < :cutoff AND (endTime + INTERVAL 0 SECOND) IS NOT NULL";
  private static final String PRUNE_CYPHER =
      "DELETE FROM log_cypher WHERE id >= :lo AND id < :hi AND " + PRUNE_AGGREGATED
          + " AND (logTime + INTERVAL 0 SECOND) < :cutoff";

  /** At most this many ids go into one IN list. */
  private static final int ID_CHUNK = 5000;

  /**
   * Read a batch of frozen request rows and fold them into {@code acc}.
   *
   * @return {@code {rowsRead, maxIdInBatch, rowsFolded}}; {@code maxIdInBatch == fromId} when nothing was read. A
   * row without a timestamp is read but not folded, since no hour bucket can hold it.
   */
  @SuppressWarnings("unchecked")
  public long[] foldHistoricalRequestBatch(RollupAccumulators acc, long fromId, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_HIST_REQUEST)
        .setParameter("fromId", fromId).setParameter("lim", limit).getResultList();
    long maxId = fromId;
    long folded = 0;
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
      folded++;
    }
    return new long[]{rows.size(), maxId, folded};
  }

  /** The Cypher counterpart of {@link #foldHistoricalRequestBatch}, with the same result. */
  @SuppressWarnings("unchecked")
  public long[] foldHistoricalCypherBatch(RollupAccumulators acc, long fromId, int limit) {
    List<Object[]> rows = currentSession().createNativeQuery(SELECT_HIST_CYPHER)
        .setParameter("fromId", fromId).setParameter("lim", limit).getResultList();
    long maxId = fromId;
    long folded = 0;
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
      folded++;
    }
    return new long[]{rows.size(), maxId, folded};
  }

  // ---- live path (current tables, per settled UTC day) ------------------------------------------

  /** @return exact IDs locked and folded by this transaction; other workers may own skipped rows. */
  public List<Long> foldLiveRequestBatch(RollupAccumulators acc, Instant dayStart, Instant dayEnd, int limit) {
    List<Long> ids = liveIds(SELECT_LIVE_REQUEST_IDS, dayStart, dayEnd, limit);
    for (Object[] r : rowsById(SELECT_LIVE_REQUEST_ROWS, ids)) {
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
    return ids;
  }

  public List<Long> foldLiveCypherBatch(RollupAccumulators acc, Instant dayStart, Instant dayEnd, int limit) {
    List<Long> ids = liveIds(SELECT_LIVE_CYPHER_IDS, dayStart, dayEnd, limit);
    for (Object[] r : rowsById(SELECT_LIVE_CYPHER_ROWS, ids)) {
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
    return ids;
  }

  /** Step one of a live batch: lock up to {@code limit} unaggregated rows of the day and return their ids. */
  private List<Long> liveIds(String sql, Instant dayStart, Instant dayEnd, int limit) {
    List<?> rows = currentSession().createNativeQuery(sql)
        .setParameter("ds", sqlTime(dayStart))
        .setParameter("de", sqlTime(dayEnd))
        .setParameter("lim", limit).getResultList();
    List<Long> ids = new ArrayList<>(rows.size());
    for (Object o : rows) {
      ids.add(num(o));
    }
    return ids;
  }

  /** Step two: the rows themselves, by primary key. This transaction already holds their locks. */
  @SuppressWarnings("unchecked")
  private List<Object[]> rowsById(String sql, List<Long> ids) {
    List<Object[]> rows = new ArrayList<>(ids.size());
    for (List<Long> chunk : chunks(ids)) {
      rows.addAll(currentSession().createNativeQuery(sql).setParameterList("ids", chunk).getResultList());
    }
    return rows;
  }

  /** Never mark a range: a smaller ID may have committed since the SELECT. */
  public int markLiveRequestRows(List<Long> ids, Instant now) {
    return markRows("log_request", ids, now);
  }

  public int markLiveCypherRows(List<Long> ids, Instant now) {
    return markRows("log_cypher", ids, now);
  }

  private int markRows(String table, List<Long> ids, Instant now) {
    if (ids.isEmpty()) return 0;
    int marked = 0;
    for (List<Long> chunk : chunks(ids)) {
      marked += currentSession().createNativeMutationQuery("UPDATE " + table
          + " SET aggregatedAt = :now WHERE aggregatedAt IS NULL AND id IN (:ids)")
          .setParameter("now", sqlTime(now)).setParameterList("ids", chunk).executeUpdate();
    }
    if (marked != ids.size()) throw new IllegalStateException("Log batch lost ownership before marking");
    return marked;
  }

  /**
   * Earliest not-yet-aggregated timestamp in a live table, or null if all are aggregated. Not {@code MIN()}: the
   * completeness check is not in the (aggregatedAt, time) index, so MIN would read every unaggregated row, where an
   * ordered LIMIT 1 walks the index from the earliest and stops at the first complete row.
   */
  public Instant earliestUnaggregated(String timeColumn, String table) {
    String col = safeCol(timeColumn);
    List<?> first = currentSession()
        .createNativeQuery("SELECT " + asText(col) + " FROM " + safe(table) + " WHERE aggregatedAt IS NULL AND "
            + ("log_request".equals(table) ? COMPLETE_REQUEST : col + " IS NOT NULL") + " ORDER BY " + col + " LIMIT 1")
        .getResultList();
    return first.isEmpty() ? null : ts(first.get(0));
  }

  // ---- prune by primary-key windows -------------------------------------------------------------

  /**
   * The ids a prune pass walks: from the table's lowest id up to the first row it must keep, found through the time
   * index. That is the first row at or after the cutoff, or after the earliest row still waiting for aggregation if
   * that comes sooner. Ids roughly follow time, and every row in the range is still checked by time, so a row on the
   * wrong side of the bound is only left for a later pass.
   *
   * @return {@code {fromId, toId}}, {@code toId} exclusive; empty when {@code fromId >= toId}
   */
  public long[] requestPruneRange(Instant cutoff) {
    return pruneRange("log_request", "requestTime", cutoff, earliestUnaggregated("requestTime", "log_request"));
  }

  public long[] cypherPruneRange(Instant cutoff) {
    return pruneRange("log_cypher", "logTime", cutoff, earliestUnaggregated("logTime", "log_cypher"));
  }

  private long[] pruneRange(String table, String timeColumn, Instant cutoff, Instant earliestUnaggregated) {
    Instant keepFrom = earliestUnaggregated != null && earliestUnaggregated.isBefore(cutoff) ? earliestUnaggregated
        : cutoff;
    long from = num(currentSession().createNativeQuery("SELECT COALESCE(MIN(id), 0) FROM " + safe(table))
        .getSingleResult());
    List<?> first = currentSession().createNativeQuery("SELECT id FROM " + safe(table) + " WHERE "
            + safeCol(timeColumn) + " >= :t ORDER BY " + safeCol(timeColumn) + " LIMIT 1")
        .setParameter("t", sqlTime(keepFrom)).getResultList();
    long to = first.isEmpty() ? maxId(table) + 1 : num(first.get(0));
    return new long[]{from, to};
  }

  /**
   * Delete the prunable rows in one window {@code [fromId, fromId + step)}, after first skipping to the next id that
   * exists, so a range already pruned is crossed in one lookup rather than window by window.
   *
   * @return {@code {nextFromId, deleted}}; {@code nextFromId} is -1 once no row is left before {@code toId}
   */
  public long[] pruneRequestWindow(long fromId, long toId, Instant cutoff, int step) {
    return pruneWindow("log_request", PRUNE_REQUEST, fromId, toId, cutoff, step);
  }

  public long[] pruneCypherWindow(long fromId, long toId, Instant cutoff, int step) {
    return pruneWindow("log_cypher", PRUNE_CYPHER, fromId, toId, cutoff, step);
  }

  private long[] pruneWindow(String table, String sql, long fromId, long toId, Instant cutoff, int step) {
    Object next = currentSession().createNativeQuery("SELECT MIN(id) FROM " + safe(table)
            + " WHERE id >= :lo AND id < :hi")
        .setParameter("lo", fromId).setParameter("hi", toId).getSingleResult();
    if (next == null) {
      return new long[]{-1, 0};
    }
    long lo = num(next);
    long hi = Math.min(lo + Math.max(step, 1), toId);
    int deleted = currentSession().createNativeMutationQuery(sql)
        .setParameter("lo", lo).setParameter("hi", hi).setParameter("cutoff", sqlTime(cutoff)).executeUpdate();
    return new long[]{hi < toId ? hi : -1, deleted};
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
    String ds = sqlTime(from), de = sqlTime(to);
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
          .setParameter("ds", sqlTime(from)).setParameter("de", sqlTime(to)).executeUpdate();
    }
    String where = bounded ? " WHERE logTime >= :ds AND logTime < :de" : "";
    var q = currentSession().createNativeMutationQuery("INSERT INTO agg_cypher_outlier " + CYP_OUT_COLS
        + " SELECT logTime, systemComponentName, operation, runnableHash, duration, runnable, interpolated, "
        + "parameters, className, methodName FROM " + safe(table) + where + " ORDER BY duration DESC LIMIT :n");
    if (bounded) {
      q.setParameter("ds", sqlTime(from)).setParameter("de", sqlTime(to));
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
        .setParameter("hourUtc", sqlTime(k.hourUtc()))
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
        .setParameter("hourUtc", sqlTime(k.hourUtc()))
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
        .setParameter("hourUtc", sqlTime(k.hourUtc()))
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
        .setParameter("firstSeen", sqlTime(c.firstSeen))
        .setParameter("lastSeen", sqlTime(c.lastSeen))
        .executeUpdate();
  }

  // ---- helpers -----------------------------------------------------------------------------------

  private static long num(Object o) {
    return o == null ? 0L : ((Number) o).longValue();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

  /** Select a DATETIME column (or expression) as its stored text, so that no driver time zone touches it. */
  private static String asText(String column) {
    return "DATE_FORMAT(" + column + ", '%Y-%m-%d %H:%i:%s.%f')";
  }

  /** An instant as the UTC wall-clock text the log tables store; MariaDB compares and stores it as a DATETIME. */
  static String sqlTime(Instant instant) {
    return instant == null ? null : SQL_TIME.format(instant.atOffset(ZoneOffset.UTC));
  }

  /** The instant of a time selected {@link #asText}. */
  static Instant ts(Object o) {
    return o == null ? null : LocalDateTime.parse(o.toString(), SQL_TIME).toInstant(ZoneOffset.UTC);
  }

  private static List<List<Long>> chunks(List<Long> ids) {
    List<List<Long>> chunks = new ArrayList<>();
    for (int i = 0; i < ids.size(); i += ID_CHUNK) {
      chunks.add(ids.subList(i, Math.min(i + ID_CHUNK, ids.size())));
    }
    return chunks;
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
