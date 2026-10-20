package org.metadatacenter.server.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.agg.LogAggregationService;
import org.metadatacenter.server.logging.dbmodel.agg.LogAggregationState;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.metadatacenter.server.logging.EmbeddedLogStore.at;

/**
 * The worker writes each request to the log database in fragments as they arrive, and each Cypher query whole. The
 * aggregation folds a settled day's complete requests and queries into hourly rollups and marks the rows it folded,
 * keeps the day's slowest and failed instances, and the prune later deletes what was folded. The one-time backfill
 * folds the frozen history the same way, resumably, and checks that it read every row. Every step runs here against
 * MariaDB, with the SQL the servers run.
 */
@Tag("integration")
class LogAggregationStoreTest {

  private static final Instant DAY = Instant.parse("2026-03-10T00:00:00Z");
  private static final Instant NEXT_DAY = DAY.plus(1, ChronoUnit.DAYS);
  private static final String USER = "https://metadatacenter.org/users/u-1";

  private EmbeddedLogStore store;

  @BeforeEach
  void startTheStore() {
    store = EmbeddedLogStore.start();
  }

  @Test
  void aRequestIsAssembledFromItsFragmentsInAnyOrderAndOnlyOnce() {
    Instant at = DAY.plus(10, ChronoUnit.HOURS);
    List<AppLogMessage> fragments = request("r-1", at, 120, 200);
    store.writer.handleLog(fragments.get(2));
    store.writer.handleLog(fragments.get(0));
    store.writer.handleLog(fragments.get(1));
    store.writer.handleLog(fragments.get(1));

    List<Object[]> rows = store.rows("SELECT systemComponentName, httpMethod, path, className, methodName, userId, "
        + "authSource, status, handlerDuration, preHandlerDuration, type, subType FROM log_request");
    assertEquals(1, rows.size(), "one row per request, whatever order its fragments arrive in");
    Object[] row = rows.get(0);
    assertEquals(List.of("resource", "GET", "/templates/t-1", "TemplatesResource", "findTemplate", USER, "TOKEN"),
        List.of(row).subList(0, 7));
    assertEquals(200, ((Number) row[7]).intValue());
    assertEquals(Duration.ofMillis(120).toNanos(), ((Number) row[8]).longValue(), "the handler ran from start to end");
    assertEquals(Duration.ofMillis(5).toNanos(), ((Number) row[9]).longValue(), "the filter ran before the handler");
    assertEquals(List.of("requestHandler", "full"), List.of(row[10], row[11]));
    assertEquals(1, store.count("SELECT COUNT(*) FROM log_event_receipt"), "one receipt fences every phase");
  }

  @Test
  void aSettledDayIsFoldedIntoHourlyRollupsAndItsRowsAreMarked() {
    Instant ten = DAY.plus(10, ChronoUnit.HOURS);
    write(request("r-1", ten.plusSeconds(60), 100, 200));
    write(request("r-2", ten.plusSeconds(120), 300, 200));
    write(request("r-3", ten.plusSeconds(180), 900, 500));
    store.writer.handleLog(exception("r-3", ten.plusSeconds(181)));
    write(request("r-4", DAY.plus(14, ChronoUnit.HOURS), 50, 404));
    write(request("r-next", NEXT_DAY.plusSeconds(60), 10, 200));
    store.writer.handleLog(cypher(ten.plusSeconds(61), "MATCH (n) RETURN n", "h-1", 40));
    store.writer.handleLog(cypher(ten.plusSeconds(62), "MATCH (n) RETURN n", "h-1", 60));

    assertNotNull(store.aggregation.earliestUnaggregatedRequestTime());
    assertNotNull(store.aggregation.earliestUnaggregatedCypherTime());
    // Two batches, to show that folding in batches adds up to folding at once.
    assertEquals(2, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 2));
    assertEquals(2, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 2));
    assertEquals(0, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 2), "the day is folded");
    assertEquals(2, store.aggregation.aggregateLiveCypherBatch(DAY, NEXT_DAY, 10));
    assertEquals(0, store.aggregation.aggregateLiveCypherBatch(DAY, NEXT_DAY, 10));

    List<Object[]> hourly = store.rows("SELECT statusClass, reqCount, errorCount, minHandlerNanos, maxHandlerNanos, "
        + "sumHandlerNanos FROM agg_request_hourly ORDER BY hourUtc, statusClass");
    assertEquals(3, hourly.size(), "ten o'clock's 2xx and 5xx, and two o'clock's 4xx");
    assertRollup(hourly.get(0), "2xx", 2, 0, 100, 300, 400);
    assertRollup(hourly.get(1), "5xx", 1, 1, 900, 900, 900);
    assertRollup(hourly.get(2), "4xx", 1, 1, 50, 50, 50);
    assertEquals(4, store.count("SELECT SUM(h0+h1+h2+h3+h4+h5+h6+h7+h8+h9+h10+h11+h12+h13+h14) FROM agg_request_hourly"),
        "every request is in one histogram bucket");
    assertEquals(4, store.count("SELECT SUM(reqCount) FROM agg_request_user_hourly WHERE userId = '" + USER + "'"));

    List<Object[]> cypher = store.rows("SELECT runnableHash, execCount, minNanos, maxNanos FROM agg_cypher_hourly");
    assertEquals(1, cypher.size());
    assertEquals("h-1", cypher.get(0)[0]);
    assertEquals(2, ((Number) cypher.get(0)[1]).longValue());
    assertEquals(Duration.ofMillis(40).toNanos(), ((Number) cypher.get(0)[2]).longValue());
    assertEquals(Duration.ofMillis(60).toNanos(), ((Number) cypher.get(0)[3]).longValue());
    assertEquals(1, store.count("SELECT COUNT(*) FROM agg_cypher_query_catalog WHERE runnableHash = 'h-1'"));

    assertEquals(4, store.count("SELECT COUNT(*) FROM log_request WHERE aggregatedAt IS NOT NULL"));
    assertEquals(List.of("r-next"), store.column("SELECT localRequestId FROM log_request WHERE aggregatedAt IS NULL"),
        "the next day is not folded with this one");
    assertNull(store.aggregation.earliestUnaggregatedCypherTime());
  }

  @Test
  void anIncompleteRequestWaitsForItsLastFragment() {
    List<AppLogMessage> fragments = request("r-1", DAY.plus(3, ChronoUnit.HOURS), 100, 200);
    store.writer.handleLog(fragments.get(0));
    store.writer.handleLog(fragments.get(1));

    assertNull(store.aggregation.earliestUnaggregatedRequestTime(), "a request without its end is not complete");
    assertEquals(0, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 10));

    store.writer.handleLog(fragments.get(2));
    assertEquals(1, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 10));
  }

  @Test
  void aDaysSlowestAndFailedInstancesAreKeptAndCapturingTwiceKeepsThemOnce() {
    Instant ten = DAY.plus(10, ChronoUnit.HOURS);
    write(request("r-1", ten, 100, 200));
    write(request("r-2", ten.plusSeconds(1), 700, 500));
    store.writer.handleLog(exception("r-2", ten.plusSeconds(2)));
    store.writer.handleLog(cypher(ten.plusSeconds(3), "MATCH (n) RETURN n", "h-1", 40));

    for (int i = 0; i < 2; i++) {
      store.aggregation.captureLiveRequestDayOutliers(DAY, NEXT_DAY);
      store.aggregation.captureLiveCypherDayOutliers(DAY, NEXT_DAY);
    }

    assertEquals(2, store.count("SELECT COUNT(*) FROM agg_request_outlier WHERE kind = 'SLOW'"));
    List<Object[]> errors = store.rows("SELECT path, status, errorPack FROM agg_request_outlier WHERE kind = 'ERROR'");
    assertEquals(1, errors.size());
    assertEquals(500, ((Number) errors.get(0)[1]).intValue());
    assertTrue(errors.get(0)[2].toString().contains("the store is down"), "the error is kept with the instance");
    assertEquals(1, store.count("SELECT COUNT(*) FROM agg_cypher_outlier"));
  }

  @Test
  void thePruneDeletesOnlyFoldedRowsPastTheRetention() {
    write(request("r-old", DAY.plus(10, ChronoUnit.HOURS), 100, 200));
    write(request("r-unfolded", NEXT_DAY.plus(10, ChronoUnit.HOURS), 100, 200));
    store.writer.handleLog(cypher(DAY.plus(10, ChronoUnit.HOURS), "MATCH (n) RETURN n", "h-1", 40));
    assertEquals(1, store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 10));
    assertEquals(1, store.aggregation.aggregateLiveCypherBatch(DAY, NEXT_DAY, 10));

    assertEquals(0, store.aggregation.pruneRequests(DAY, 10), "nothing is past a cutoff before the day");
    assertEquals(1, store.aggregation.pruneRequests(NEXT_DAY, 10));
    assertEquals(1, store.aggregation.pruneCypher(NEXT_DAY, 10));

    assertEquals(List.of("r-unfolded"), store.column("SELECT localRequestId FROM log_request"));
    assertEquals(3, store.count("SELECT COUNT(*) FROM log_event_receipt"),
        "the pruned rows' receipts still fence a replay of them");
  }

  @Test
  void theBackfillFoldsTheHistoryResumablyAndChecksItReadEveryRow() {
    // The frozen tables hold what the live tables held before the rollups existed.
    Instant ten = DAY.plus(10, ChronoUnit.HOURS);
    write(request("h-1", ten, 100, 200));
    write(request("h-2", ten.plusSeconds(1), 200, 500));
    store.writer.handleLog(exception("h-2", ten.plusSeconds(2)));
    write(request("h-3", ten.plusSeconds(3), 300, 200));
    store.writer.handleLog(cypher(ten, "MATCH (n) RETURN n", "c-1", 40));
    store.execute("INSERT INTO log_request_pre284 SELECT * FROM log_request");
    store.execute("INSERT INTO log_cypher_pre284 SELECT * FROM log_cypher");
    store.execute("INSERT INTO log_request_pre284 (localRequestId, handlerDuration, preHandlerDuration, lineNumber) "
        + "VALUES ('no-time', 0, 0, 0)");
    store.execute("DELETE FROM log_request");
    store.execute("DELETE FROM log_cypher");

    store.aggregation.initBackfillState(LogAggregationService.SRC_REQUEST_HIST);
    store.aggregation.initBackfillState(LogAggregationService.SRC_CYPHER_HIST);
    long read = 0;
    for (long batch; (batch = store.aggregation.runBackfillRequestBatch(2)) > 0; ) {
      read += batch;
    }
    assertEquals(4, read);
    assertEquals(1, store.aggregation.runBackfillCypherBatch(10));
    assertEquals(0, store.aggregation.runBackfillCypherBatch(10));
    store.aggregation.initBackfillState(LogAggregationService.SRC_REQUEST_HIST);
    assertEquals(0, store.aggregation.runBackfillRequestBatch(2), "a restarted backfill resumes from its cursor");
    store.aggregation.markBackfillComplete(LogAggregationService.SRC_REQUEST_HIST);
    store.aggregation.markBackfillComplete(LogAggregationService.SRC_CYPHER_HIST);
    store.aggregation.captureHistoryOutliers();

    // History has no status: a request is an error or unknown.
    List<Object[]> hourly = store.rows("SELECT statusClass, reqCount, errorCount FROM agg_request_hourly "
        + "ORDER BY statusClass");
    assertEquals(2, hourly.size());
    assertEquals(List.of("err", 1L, 1L), List.of(hourly.get(0)[0], num(hourly.get(0)[1]), num(hourly.get(0)[2])));
    assertEquals(List.of("unknown", 2L, 0L), List.of(hourly.get(1)[0], num(hourly.get(1)[1]), num(hourly.get(1)[2])));
    assertEquals(1, store.count("SELECT SUM(execCount) FROM agg_cypher_hourly"));

    Map<String, Object[]> states = store.rows("SELECT sourceTable, status, rowsIn, errorText FROM log_aggregation_state")
        .stream().collect(java.util.stream.Collectors.toMap(row -> (String) row[0], row -> row));
    Object[] requests = states.get(LogAggregationService.SRC_REQUEST_HIST);
    assertEquals(LogAggregationState.Status.READY_TO_DROP.name(), requests[1]);
    assertEquals(3L, num(requests[2]), "three of the four rows read could be placed in an hour");
    assertNotNull(requests[3], "the row without a time was not aggregated, and the parity check says so");
    Object[] queries = states.get(LogAggregationService.SRC_CYPHER_HIST);
    assertEquals(LogAggregationState.Status.READY_TO_DROP.name(), queries[1]);
    assertNull(queries[3], "every history query was folded");

    assertEquals(4, store.count("SELECT COUNT(*) FROM agg_request_outlier WHERE kind = 'SLOW'"));
    assertEquals(1, store.count("SELECT COUNT(*) FROM agg_request_outlier WHERE kind = 'ERROR'"));
    assertEquals(1, store.count("SELECT COUNT(*) FROM agg_cypher_outlier"));
  }

  private void write(List<AppLogMessage> fragments) {
    fragments.forEach(store.writer::handleLog);
  }

  /** The three fragments of one request to the resource server, as its filters and handler log them. */
  static List<AppLogMessage> request(String localId, Instant at, long handlerMillis, int status) {
    String global = "g-" + localId;
    AppLogMessage start = at(at, new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.REQUEST_FILTER,
        AppLogSubType.START, global, localId)
        .param(AppLogParam.HTTP_METHOD, "GET")
        .param(AppLogParam.PATH, "/templates/t-1")
        .param(AppLogParam.GLOBAL_REQUEST_ID_SOURCE, "NEW")
        .param(AppLogParam.QUERY_PARAMETERS, Map.of("format", "json")));
    Instant handlerStart = at.plusMillis(5);
    AppLogMessage handler = at(handlerStart, new AppLogMessage(SystemComponent.SERVER_RESOURCE,
        AppLogType.REQUEST_HANDLER, AppLogSubType.START, global, localId)
        .param(AppLogParam.CLASS_NAME, "TemplatesResource")
        .param(AppLogParam.METHOD_NAME, "findTemplate")
        .param(AppLogParam.LINE_NUMBER, 42)
        .param(AppLogParam.USER_ID, USER)
        .param(AppLogParam.AUTH_SOURCE, "TOKEN")
        .param(AppLogParam.API_KEY_HASH, "key-hash"));
    AppLogMessage end = at(handlerStart.plusMillis(handlerMillis), new AppLogMessage(SystemComponent.SERVER_RESOURCE,
        AppLogType.REQUEST_FILTER, AppLogSubType.END, global, localId)
        .param(AppLogParam.STATUS, status));
    return List.of(start, handler, end);
  }

  static AppLogMessage exception(String localId, Instant at) {
    return at(at, new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.RESPONSE_EXCEPTION,
        AppLogSubType.FULL, "g-" + localId, localId)
        .param(AppLogParam.EXCEPTION, Map.of("message", "the store is down")));
  }

  static AppLogMessage cypher(Instant at, String query, String hash, long millis) {
    return cypher(at, query, hash, millis, "cypher");
  }

  /** A Cypher query the resource server ran while handling the request with the given local id. */
  static AppLogMessage cypher(Instant at, String query, String hash, long millis, String localId) {
    AppLogMessage message = new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.CYPHER_QUERY,
        AppLogSubType.FULL, "g-" + localId, localId)
        .param(AppLogParam.ORIGINAL_QUERY, query)
        .param(AppLogParam.RUNNABLE_QUERY, query)
        .param(AppLogParam.INTERPOLATED_QUERY, query)
        .param(AppLogParam.RUNNABLE_QUERY_HASH, hash)
        .param(AppLogParam.QUERY_PARAMETERS_HASH, "p-" + hash)
        .param(AppLogParam.CLASS_NAME, "Neo4JProxyResource")
        .param(AppLogParam.METHOD_NAME, "findResource")
        .param(AppLogParam.LINE_NUMBER, 7)
        .param(AppLogParam.OPERATION, "READ")
        .param(AppLogParam.QUERY_PARAMETERS, Map.of("id", "t-1"));
    message.setStartTime(at);
    message.setEndTime(at.plusMillis(millis));
    message.setDuration(Duration.ofMillis(millis));
    return at(at, message);
  }

  private static void assertRollup(Object[] row, String statusClass, long count, long errors, long minMillis,
                                   long maxMillis, long sumMillis) {
    assertEquals(statusClass, row[0]);
    assertEquals(count, num(row[1]), statusClass + " count");
    assertEquals(errors, num(row[2]), statusClass + " errors");
    assertEquals(Duration.ofMillis(minMillis).toNanos(), num(row[3]), statusClass + " minimum");
    assertEquals(Duration.ofMillis(maxMillis).toNanos(), num(row[4]), statusClass + " maximum");
    assertEquals(Duration.ofMillis(sumMillis).toNanos(), num(row[5]), statusClass + " sum");
  }

  private static long num(Object value) {
    return ((Number) value).longValue();
  }
}
