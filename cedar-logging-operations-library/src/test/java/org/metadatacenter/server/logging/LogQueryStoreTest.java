package org.metadatacenter.server.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.logging.agg.AggQueryResults.CypherStat;
import org.metadatacenter.server.logging.agg.AggQueryResults.EndpointStat;
import org.metadatacenter.server.logging.agg.AggQueryResults.TimeBucket;
import org.metadatacenter.server.logging.agg.AggQueryResults.UsageTotals;
import org.metadatacenter.server.logging.agg.AggQueryResults.UserStat;
import org.metadatacenter.server.logging.agg.LogExplorerResults.RequestRow;
import org.metadatacenter.server.logging.dao.agg.AggregationQueryDAO;
import org.metadatacenter.server.logging.dao.agg.LogExplorerDAO;
import org.metadatacenter.server.logging.dao.query.LogQueryDAO;
import org.metadatacenter.server.logging.query.LogBoards;
import org.metadatacenter.server.logging.query.LogQueryResults.FacetResult;
import org.metadatacenter.server.logging.query.LogQueryResults.QueryResult;
import org.metadatacenter.server.logging.query.LogQueryResults.TraceResult;
import org.metadatacenter.server.logging.query.LogQuerySpec;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.metadatacenter.server.logging.LogAggregationStoreTest.cypher;
import static org.metadatacenter.server.logging.LogAggregationStoreTest.exception;
import static org.metadatacenter.server.logging.LogAggregationStoreTest.request;

/**
 * The monitor's log pages read the store three ways: the usage page reads the hourly rollups, the explorer pages the
 * raw log and the kept outliers, and the query engine answers a structured question, which every saved board is. Each
 * runs here against MariaDB over one day of traffic: three requests to one endpoint, one of them failing and one not
 * found, and two Cypher queries run by the first.
 */
@Tag("integration")
class LogQueryStoreTest {

  private static final Instant DAY = Instant.parse("2026-03-10T00:00:00Z");
  private static final Instant NEXT_DAY = DAY.plus(1, ChronoUnit.DAYS);
  /** A range wide enough to hold the day whatever zone the connection reads it in. */
  private static final Instant FROM = DAY.minus(1, ChronoUnit.DAYS);
  private static final Instant TO = NEXT_DAY.plus(1, ChronoUnit.DAYS);

  private EmbeddedLogStore store;
  private AggregationQueryDAO usage;
  private LogExplorerDAO explorer;
  private LogQueryDAO queries;

  @BeforeEach
  void writeADayOfTraffic() {
    store = EmbeddedLogStore.start();
    usage = new AggregationQueryDAO(store.sessionFactory);
    explorer = new LogExplorerDAO(store.sessionFactory);
    queries = new LogQueryDAO(store.sessionFactory);
    Instant ten = DAY.plus(10, ChronoUnit.HOURS);
    request("r-1", ten, 100, 200).forEach(store.writer::handleLog);
    request("r-2", ten.plusSeconds(300), 700, 500).forEach(store.writer::handleLog);
    store.writer.handleLog(exception("r-2", ten.plusSeconds(301)));
    request("r-3", ten.plus(1, ChronoUnit.HOURS), 50, 404).forEach(store.writer::handleLog);
    store.writer.handleLog(cypher(ten.plusMillis(10), "MATCH (n) RETURN n", "h-1", 40, "r-1"));
    store.writer.handleLog(cypher(ten.plusMillis(60), "MATCH (n) RETURN n", "h-1", 30, "r-1"));
    store.aggregation.aggregateLiveRequestBatch(DAY, NEXT_DAY, 100);
    store.aggregation.aggregateLiveCypherBatch(DAY, NEXT_DAY, 100);
  }

  @Test
  void theUsagePageReadsTheRollups() {
    List<EndpointStat> endpoints = store.inTransaction(session -> usage.endpointBreakdown(FROM, TO, 10));
    assertEquals(1, endpoints.size());
    EndpointStat endpoint = endpoints.get(0);
    assertEquals(List.of("resource", "TemplatesResource", "findTemplate", "GET"),
        List.of(endpoint.component(), endpoint.className(), endpoint.methodName(), endpoint.httpMethod()));
    assertEquals(3, endpoint.reqCount());
    assertEquals(2, endpoint.errorCount(), "the 404 and the 500");
    assertEquals(Duration.ofMillis(700).toNanos(), endpoint.maxNanos());
    assertTrue(endpoint.p50Nanos() > 0 && endpoint.p50Nanos() <= endpoint.p99Nanos(), endpoint.toString());
    assertEquals(List.of(), store.inTransaction(session -> usage.endpointBreakdown(FROM, TO, 10, 1)),
        "the second page of one endpoint is empty");

    List<CypherStat> statements = store.inTransaction(session -> usage.cypherBreakdown(FROM, TO, 10));
    assertEquals(1, statements.size());
    assertEquals("h-1", statements.get(0).runnableHash());
    assertEquals("MATCH (n) RETURN n", statements.get(0).sample(), "the catalog supplies the query's text");
    assertEquals(2, statements.get(0).execCount());
    assertEquals(Duration.ofMillis(40).toNanos(), statements.get(0).maxNanos());

    List<UserStat> users = store.inTransaction(session -> usage.userBreakdown(FROM, TO, 10));
    assertEquals(1, users.size());
    assertEquals("https://metadatacenter.org/users/u-1", users.get(0).userId());
    assertEquals(3, users.get(0).reqCount());
    assertEquals(2, users.get(0).errorCount());

    assertEquals(List.of(1L, 1L, 1L), store.inTransaction(session -> List.of(usage.countEndpoints(FROM, TO),
        usage.countCypherStatements(FROM, TO), usage.countUsers(FROM, TO))));
    List<TimeBucket> volume = store.inTransaction(session -> usage.volumeSeries(FROM, TO));
    assertEquals(2, volume.size(), "ten and eleven o'clock");
    assertEquals(3, volume.stream().mapToLong(TimeBucket::reqCount).sum());
    UsageTotals totals = store.inTransaction(session -> usage.totals(FROM, TO));
    assertEquals(3, totals.reqCount());
    assertEquals(2, totals.errorCount());
    assertTrue(totals.p50Nanos() <= totals.p95Nanos() && totals.p95Nanos() <= totals.p99Nanos(), totals.toString());
  }

  @Test
  void theExplorerPagesTheRawLogNewestFirst() {
    List<RequestRow> first = store.inTransaction(session -> explorer.recentRequests("", 0, 2));
    List<RequestRow> second = store.inTransaction(session -> explorer.recentRequests("", 0, 2, 2));
    assertEquals(List.of("g-r-3", "g-r-2"), first.stream().map(RequestRow::globalRequestId).toList());
    assertEquals(List.of("g-r-1"), second.stream().map(RequestRow::globalRequestId).toList());
    RequestRow failed = first.get(1);
    assertEquals("TemplatesResource.findTemplate()", failed.handler());
    assertEquals(500, failed.status());
    assertTrue(failed.errorPack().contains("the store is down"), failed.errorPack());

    assertEquals(List.of("g-r-2"), store.inTransaction(session -> explorer.recentRequests("templates", 500_000_000L, 10))
        .stream().map(RequestRow::globalRequestId).toList(), "a filter by text and by duration");
    assertEquals(3L, (long) store.inTransaction(session -> explorer.countRequests("TemplatesResource", 0, 10)));
    assertEquals(2L, (long) store.inTransaction(session -> explorer.countRequests("", 0, 2)), "counting stops at the cap");
    assertEquals(0L, (long) store.inTransaction(session -> explorer.countRequests("nothing-matches", 0, 10)));

    assertEquals(2, store.inTransaction(session -> explorer.recentCypher("MATCH", 0, 10)).size());
    assertEquals(1, store.inTransaction(session -> explorer.recentCypher("", 35_000_000L, 10)).size());
    assertEquals(2L, (long) store.inTransaction(session -> explorer.countCypher("h-1", 0, 10)));
  }

  @Test
  void theExplorerListsTheKeptOutliers() {
    store.aggregation.captureLiveRequestDayOutliers(DAY.minus(1, ChronoUnit.DAYS), TO);
    store.aggregation.captureLiveCypherDayOutliers(DAY.minus(1, ChronoUnit.DAYS), TO);

    List<RequestRow> slow = store.inTransaction(session -> explorer.requestOutliers("SLOW", 10));
    assertEquals(List.of(700L, 100L, 50L), slow.stream().map(row -> Duration.ofNanos(row.durationNanos()).toMillis())
        .toList(), "slowest first");
    assertEquals(List.of(500), store.inTransaction(session -> explorer.requestOutliers("ERROR", 10, 0))
        .stream().map(RequestRow::status).toList());
    assertEquals(3L, (long) store.inTransaction(session -> explorer.countRequestOutliers("SLOW")));
    assertEquals(2, store.inTransaction(session -> explorer.cypherOutliers(10)).size());
    assertEquals(1, store.inTransaction(session -> explorer.cypherOutliers(10, 1)).size());
    assertEquals(2L, (long) store.inTransaction(session -> explorer.countCypherOutliers()));
  }

  /** The catalog is server-owned and the page renders whatever it lists, so a board that fails is a broken entry. */
  @Test
  void everySavedBoardRunsAgainstTheStore() {
    List<String> failures = new ArrayList<>();
    int answered = 0;
    for (LogBoards.Board board : LogBoards.all()) {
      if (board.spec() == null) {
        continue;
      }
      LogQuerySpec s = board.spec();
      LogQuerySpec ranged = new LogQuerySpec(s.table(), FROM.toString(), TO.toString(), s.filters(), s.groupBy(),
          s.metrics(), s.orderBy(), s.limit(), s.cursor(), s.having(), s.source());
      try {
        QueryResult result = store.inTransaction(session -> queries.query(ranged));
        if (result.rowCount() > 0) {
          answered++;
        }
      } catch (RuntimeException e) {
        failures.add(board.id() + " -> " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
    if (!failures.isEmpty()) {
      fail("Boards that fail against the store (" + failures.size() + "):\n  " + String.join("\n  ", failures));
    }
    assertTrue(answered > 0, "the day of traffic answers some board");
  }

  @Test
  void aRawQueryIsPagedByItsCursor() {
    LogQuerySpec firstPage = new LogQuerySpec("request", FROM.toString(), TO.toString(), null, null, null, null, 2,
        null);
    QueryResult first = store.inTransaction(session -> queries.query(firstPage));
    assertEquals(2, first.rowCount());
    assertTrue(first.truncated());
    assertNotNull(first.nextCursor());
    assertTrue(first.exact(), "the raw log answers exactly");

    LogQuerySpec secondPage = new LogQuerySpec("request", FROM.toString(), TO.toString(), null, null, null, null, 2,
        first.nextCursor());
    QueryResult second = store.inTransaction(session -> queries.query(secondPage));
    assertEquals(1, second.rowCount());
    assertFalse(second.truncated());

    LogQuerySpec grouped = new LogQuerySpec("request", FROM.toString(), TO.toString(),
        List.of(new LogQuerySpec.Filter("statusClass", "ne", "ok", null)), List.of("statusClass"), List.of("count"),
        null, 10, null);
    QueryResult byStatus = store.inTransaction(session -> queries.query(grouped));
    assertEquals(2, byStatus.rowCount(), "the 4xx and the 5xx: " + byStatus.rows());
  }

  @Test
  void facetsCoverageFreshnessAndATraceDescribeWhatTheStoreHolds() {
    FacetResult components = store.inTransaction(session -> queries.facet("request", "component", FROM, TO));
    assertEquals(1, components.values().size());
    assertEquals("resource", components.values().get(0).value());
    assertEquals(3, components.values().get(0).count());

    var coverage = store.inTransaction(session -> queries.coverage());
    Map<String, Long> rows = new java.util.HashMap<>();
    coverage.tables().forEach(table -> rows.put(table.sqlTable(), table.rowCount()));
    assertEquals(3L, rows.get("log_request"));
    assertEquals(2L, rows.get("log_cypher"));

    var freshness = store.inTransaction(session -> queries.freshness());
    assertEquals(2, freshness.size());
    assertTrue(freshness.stream().allMatch(table -> table.newestAt() != null && table.lagSeconds() != null),
        freshness.toString());

    TraceResult trace = store.inTransaction(session -> queries.trace("g-r-1", 100));
    assertEquals(1, trace.requestCount());
    assertEquals(2, trace.cypherCount());
    assertEquals(3, trace.spans().size());
    assertTrue(trace.dbMs() > 0 && trace.dbSharePct() > 0, trace.toString());

    QueryResult share = store.inTransaction(session -> queries.dbTimeShare(FROM, TO, 10));
    assertEquals(1, share.rowCount(), "one handler: " + share.rows());
  }

  /**
   * 02:30 on 2027-03-14 is a valid UTC time that does not exist on a Los Angeles clock. Read as a Timestamp through the
   * connection's zone it threw "HOUR_OF_DAY: 2 -> 3", and every other time came back shifted by the offset, with every
   * range bound shifted the other way. Every read path gets the gap hour, bounded to exactly that hour, and must
   * return the row at its UTC instant.
   */
  @Test
  void everyReadPathReturnsTheSpringForwardHourAtItsUtcInstant() {
    Instant gap = Instant.parse("2027-03-14T02:30:00Z");
    Instant hour = gap.truncatedTo(ChronoUnit.HOURS);
    Instant hourEnd = hour.plus(1, ChronoUnit.HOURS);
    Instant day = gap.truncatedTo(ChronoUnit.DAYS);
    Instant dayEnd = day.plus(1, ChronoUnit.DAYS);
    request("r-gap", gap, 900, 500).forEach(store.writer::handleLog);
    store.writer.handleLog(cypher(gap, "MATCH (g) RETURN g", "h-gap", 80, "r-gap"));
    store.aggregation.aggregateLiveRequestBatch(day, dayEnd, 100);
    store.aggregation.aggregateLiveCypherBatch(day, dayEnd, 100);
    store.aggregation.captureLiveRequestDayOutliers(day, dayEnd);
    store.aggregation.captureLiveCypherDayOutliers(day, dayEnd);
    String at = "2027-03-14T02:30:00Z";
    String hourAt = "2027-03-14T02:00:00Z";

    // the explorer: raw rows and kept outliers
    assertEquals(at, store.inTransaction(session -> explorer.recentRequests("", 0, 1)).get(0).requestTime());
    assertEquals(at, store.inTransaction(session -> explorer.recentCypher("", 0, 1)).get(0).logTime());
    assertEquals(at, store.inTransaction(session -> explorer.requestOutliers("SLOW", 1)).get(0).requestTime());
    assertEquals(at, store.inTransaction(session -> explorer.cypherOutliers(1)).get(0).logTime());

    // the usage page: rollups bounded by exactly the gap hour
    assertEquals(List.of(hourAt), store.inTransaction(session -> usage.volumeSeries(hour, hourEnd))
        .stream().map(TimeBucket::hourUtc).toList());
    assertEquals(1, store.inTransaction(session -> usage.totals(hour, hourEnd)).reqCount());
    assertEquals(List.of(1L, 1L, 1L), store.inTransaction(session -> List.of(usage.countEndpoints(hour, hourEnd),
        usage.countCypherStatements(hour, hourEnd), usage.countUsers(hour, hourEnd))));
    assertEquals(0, store.inTransaction(session -> usage.totals(hourEnd, hourEnd.plus(1, ChronoUnit.HOURS)))
        .reqCount(), "the next hour is empty");

    // the query engine: raw rows, the keyset cursor, time buckets, time filters, the rollup source, a facet
    LogQuerySpec raw = new LogQuerySpec("request", hour.toString(), hourEnd.toString(), null, null, null, null, 1,
        null);
    QueryResult rows = store.inTransaction(session -> queries.query(raw));
    assertEquals(1, rows.rowCount(), rows.rows().toString());
    assertEquals(at, rows.rows().get(0).get("requestTime"));
    LogQuerySpec next = new LogQuerySpec("request", hour.toString(), hourEnd.toString(), null, null, null, null, 1,
        rows.nextCursor());
    assertEquals(0, store.inTransaction(session -> queries.query(next)).rowCount(), "the cursor pages past the row");
    LogQuerySpec cypherRaw = new LogQuerySpec("cypher", hour.toString(), hourEnd.toString(), null, null, null, null,
        10, null);
    assertEquals(at, store.inTransaction(session -> queries.query(cypherRaw)).rows().get(0).get("logTime"));

    LogQuerySpec buckets = new LogQuerySpec("request", hour.toString(), hourEnd.toString(),
        List.of(new LogQuerySpec.Filter("requestTime", "gte", at, null)), List.of("tsHour", "tsMinute"),
        List.of("count", "p50:handlerDuration"), null, 10, null);
    Map<String, Object> bucket = store.inTransaction(session -> queries.query(buckets)).rows().get(0);
    assertEquals(List.of(hourAt, at, 1L), List.of(bucket.get("tsHour"), bucket.get("tsMinute"), bucket.get("count")));
    LogQuerySpec flat = new LogQuerySpec("request", hour.toString(), hourEnd.toString(),
        List.of(new LogQuerySpec.Filter("requestTime", "eq", at, null)), List.of("tsMinute"), List.of("count"), null,
        10, null);
    assertEquals(List.of(Map.of("tsMinute", at, "count", 1L)), store.inTransaction(session -> queries.query(flat))
        .rows());
    LogQuerySpec rollup = new LogQuerySpec("request", hour.toString(), hourEnd.toString(), null, List.of("tsHour"),
        List.of("count", "p95:handlerDuration"), null, 10, null, null, "rollup");
    List<Map<String, Object>> hours = store.inTransaction(session -> queries.query(rollup)).rows();
    assertEquals(1, hours.size(), hours.toString());
    assertEquals(hourAt, hours.get(0).get("tsHour"));

    FacetResult facet = store.inTransaction(session -> queries.facet("request", "tsMinute", hour, hourEnd));
    assertEquals(List.of(at), facet.values().stream().map(v -> v.value()).toList());

    // coverage, freshness, a trace, the db-time share
    var coverage = store.inTransaction(session -> queries.coverage());
    assertTrue(coverage.tables().stream().filter(table -> table.sqlTable().equals("log_request"))
        .allMatch(table -> at.equals(table.newest())), coverage.toString());
    var freshness = store.inTransaction(session -> queries.freshness());
    assertTrue(freshness.stream().allMatch(table -> at.equals(table.newestAt())), freshness.toString());
    TraceResult trace = store.inTransaction(session -> queries.trace("g-r-gap", 100));
    assertEquals(List.of("2027-03-14T02:30:00.005Z", at), trace.spans().stream().map(s -> s.startedAt()).sorted()
        .toList(), "the handler's start, 5 ms in, and the Cypher query's");
    assertEquals(1, store.inTransaction(session -> queries.dbTimeShare(hour, hourEnd, 10)).rowCount());
  }
}
