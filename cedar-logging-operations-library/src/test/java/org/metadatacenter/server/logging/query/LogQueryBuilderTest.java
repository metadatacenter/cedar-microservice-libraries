package org.metadatacenter.server.logging.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.logging.query.LogQueryBuilder.BuiltQuery;
import org.metadatacenter.server.logging.query.LogQuerySpec.Filter;
import org.metadatacenter.server.logging.query.LogQuerySpec.Sort;

import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the spec→SQL translation. The builder is pure, so everything here runs without a
 * database — the point of keeping generation out of the DAO.
 * <p>
 * Two things these tests exist to protect: (1) no caller-supplied string ever reaches the SQL text,
 * only bind parameters; (2) the validation messages name the offending field, because they are what a
 * 400 shows the operator.
 */
class LogQueryBuilderTest {

  private static final String FROM = "2026-07-30T00:00:00Z";
  private static final String TO = "2026-07-31T00:00:00Z";

  private static LogQuerySpec spec(List<String> groupBy, List<String> metrics, List<Filter> filters) {
    return new LogQuerySpec("request", FROM, TO, filters, groupBy, metrics, null, null, null);
  }

  // ---- raw mode ----------------------------------------------------------------------------------

  @Test
  void rawModeSelectsDisplayColumnsNewestFirstAndIsPageable() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, null, null));

    assertFalse(q.grouped());
    assertTrue(q.keysetPageable());
    assertTrue(q.sql().startsWith("SELECT id AS `_id`"));
    assertTrue(q.sql().contains("FROM log_request"));
    assertTrue(q.sql().contains("ORDER BY requestTime DESC, id DESC"));
    assertTrue(q.sql().contains("LIMIT :lim"));
    assertEquals(LogQueryBuilder.DEFAULT_LIMIT, q.limit());
    // _id plus every display column
    assertEquals(13, q.columns().size());
  }

  @Test
  void cursorAddsKeysetPredicateAndBindsBothParts() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, null, null, null, 500,
        "2026-07-30T12:00:00Z,4242");
    BuiltQuery q = LogQueryBuilder.build(s);

    assertTrue(q.sql().contains("requestTime < :curTs OR (requestTime = :curTs AND id < :curId)"));
    assertEquals(4242L, q.params().get("curId"));
    assertEquals(Timestamp.from(java.time.Instant.parse("2026-07-30T12:00:00Z")), q.params().get("curTs"));
    assertEquals(500, q.limit());
  }

  @Test
  void customSortInRawModeDisablesPagingAndSaysSo() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, null, null,
        List.of(new Sort("handlerDuration", "desc")), null, null);
    BuiltQuery q = LogQueryBuilder.build(s);

    assertFalse(q.keysetPageable());
    assertTrue(q.sql().contains("ORDER BY handlerDuration DESC"));
    assertTrue(q.notes().stream().anyMatch(n -> n.contains("paging is disabled")));
  }

  @Test
  void cursorWithCustomSortIsRejected() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, null, null,
        List.of(new Sort("handlerDuration", "desc")), null, "2026-07-30T12:00:00Z,1");
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("cursor is only valid with the default sort"));
  }

  /** Metrics with no groupBy is the totals shape behind a board's KPI tiles: one row, no GROUP BY. */
  @Test
  void metricsWithoutGroupByProduceTotals() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, List.of("count", "p95:handlerDuration"), null));

    assertTrue(q.grouped());
    assertFalse(q.keysetPageable());
    assertFalse(q.sql().contains("GROUP BY"), "totals over the whole range have no grouping");
    assertTrue(q.sql().contains("COUNT(*) AS `count`"));
    assertTrue(q.sql().contains("ROW_NUMBER() OVER ( ORDER BY handlerDuration)"),
        "an empty PARTITION BY still ranks the whole set");
    assertEquals(2, q.columns().size());
  }

  // ---- grouped mode -----------------------------------------------------------------------------

  @Test
  void groupedModeAggregatesWithoutAWindowWhenNoPercentileIsAsked() {
    BuiltQuery q = LogQueryBuilder.build(spec(List.of("handler"),
        List.of("count", "sum:handlerDuration", "max:handlerDuration"), null));

    assertTrue(q.grouped());
    assertFalse(q.keysetPageable());
    assertFalse(q.sql().contains("ROW_NUMBER()"), "no percentile requested → no window layer");
    assertTrue(q.sql().contains("COUNT(*) AS `count`"));
    // SQL aliases are colon-free (see colonsInSqlAreOnlyBindParameters); the public keys keep "fn:col"
    assertTrue(q.sql().contains("SUM(handlerDuration) AS `sum_handlerDuration`"));
    assertTrue(q.sql().contains("MAX(handlerDuration) AS `max_handlerDuration`"));
    assertTrue(q.columns().stream().anyMatch(c -> c.key().equals("sum:handlerDuration")));
    assertTrue(q.sql().contains("GROUP BY CONCAT(COALESCE(className,'?'),'.',COALESCE(methodName,'?'))"));
    // default grouped sort is the first metric, descending
    assertTrue(q.sql().contains("ORDER BY `count` DESC"));
  }

  @Test
  void percentileWrapsInAWindowedSubqueryAndPicksTheNthRow() {
    BuiltQuery q = LogQueryBuilder.build(spec(List.of("component"),
        List.of("count", "p95:handlerDuration"), null));

    assertTrue(q.sql().contains("ROW_NUMBER() OVER (PARTITION BY systemComponentName ORDER BY handlerDuration)"));
    assertTrue(q.sql().contains("COUNT(*) OVER (PARTITION BY systemComponentName)"));
    assertTrue(q.sql().contains("CEIL(0.95 * `cnt_handlerDuration`)"));
    assertTrue(q.sql().contains("GREATEST(1,"), "percentile position must never fall below row 1");
    assertTrue(q.notes().stream().anyMatch(n -> n.contains("exact")));
  }

  @Test
  void twoPercentilesOnOneColumnShareASingleWindowPair() {
    BuiltQuery q = LogQueryBuilder.build(spec(List.of("component"),
        List.of("p50:handlerDuration", "p99:handlerDuration"), null));

    assertEquals(1, countOccurrences(q.sql(), "ROW_NUMBER() OVER"));
    assertTrue(q.sql().contains("CEIL(0.5 * `cnt_handlerDuration`)"));
    assertTrue(q.sql().contains("CEIL(0.99 * `cnt_handlerDuration`)"));
  }

  @Test
  void groupingByATimestampIsRejectedWithTheBucketHint() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(List.of("requestTime"), null, null)));
    assertTrue(e.getMessage().contains("tsMinute/tsHour/tsDay"));
  }

  @Test
  void groupingByALobIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(List.of("errorPack"), null, null)));
    assertTrue(e.getMessage().contains("LONGTEXT"));
  }

  @Test
  void aggregatingANonNumericColumnIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(List.of("component"), List.of("sum:path"), null)));
    assertTrue(e.getMessage().contains("Numeric columns only"));
  }

  @Test
  void sortingBySomethingNotSelectedIsRejected() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, List.of("component"), List.of("count"),
        List.of(new Sort("handler", "desc")), null, null);
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("Cannot sort by 'handler'"));
  }

  // ---- having -----------------------------------------------------------------------------------

  @Test
  void havingThresholdsAMetricAndBindsTheValue() {
    // the N+1 detector board: one query shape repeated inside a single request
    LogQuerySpec s = new LogQuerySpec("cypher", FROM, TO, null,
        List.of("globalRequestId", "runnableHash"), List.of("count", "sum:duration"),
        List.of(new Sort("count", "desc")), 20, null,
        List.of(new LogQuerySpec.Having("count", "gt", "5")));
    BuiltQuery q = LogQueryBuilder.build(s);

    assertTrue(q.sql().contains("HAVING `count` > :h0"));
    assertTrue(q.sql().indexOf("HAVING") > q.sql().indexOf("GROUP BY"), "HAVING must follow GROUP BY");
    assertTrue(q.sql().indexOf("HAVING") < q.sql().indexOf("ORDER BY"), "HAVING must precede ORDER BY");
    assertEquals(5L, q.params().get("h0"));
  }

  @Test
  void havingWorksInTheWindowedPercentileShapeToo() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, List.of("handler"),
        List.of("count", "p95:handlerDuration"), null, null, null,
        List.of(new LogQuerySpec.Having("p95:handlerDuration", "gte", "1000000")));
    BuiltQuery q = LogQueryBuilder.build(s);

    assertTrue(q.sql().contains("ROW_NUMBER() OVER"));
    assertTrue(q.sql().contains("HAVING `p95_handlerDuration` >= :h0"));
    assertEquals(1_000_000L, q.params().get("h0"));
  }

  @Test
  void havingOnAMetricThatWasNotSelectedIsRejected() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, List.of("handler"), List.of("count"),
        null, null, null, List.of(new LogQuerySpec.Having("sum:handlerDuration", "gt", "1")));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("must reference one of the requested metrics"));
  }

  @Test
  void havingWithoutMetricsIsRejected() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, null, null, null, null, null,
        List.of(new LogQuerySpec.Having("count", "gt", "1")));
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("Having needs metrics"));
  }

  @Test
  void noHavingMeansNoHavingClause() {
    BuiltQuery q = LogQueryBuilder.build(spec(List.of("component"), List.of("count"), null));
    assertFalse(q.sql().contains("HAVING"));
    assertFalse(q.sql().contains("/*HAVING*/"), "the placeholder must always be substituted");
  }

  // ---- filters ----------------------------------------------------------------------------------

  @Test
  void filterValuesAreBoundNeverInlined() {
    String evil = "'; DROP TABLE log_request; --";
    BuiltQuery q = LogQueryBuilder.build(spec(null, null,
        List.of(new Filter("path", "like", evil, null))));

    assertFalse(q.sql().contains("DROP TABLE"), "value must not reach the SQL text");
    assertTrue(q.sql().contains("path LIKE :f0"));
    assertEquals("%" + evil + "%", q.params().get("f0"));
  }

  @Test
  void unknownColumnIsRejectedByName() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(null, null, List.of(new Filter("passwordHash", "eq", "x", null)))));
    assertTrue(e.getMessage().contains("Unknown column 'passwordHash'"));
  }

  @Test
  void unknownOpIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(null, null, List.of(new Filter("path", "regexp", "x", null)))));
    assertTrue(e.getMessage().contains("Unknown filter op 'regexp'"));
  }

  @Test
  void inListExpandsToOneParameterPerValue() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, null,
        List.of(new Filter("component", "in", null, List.of("a", "b", "c")))));

    assertTrue(q.sql().contains("systemComponentName IN (:f0_0, :f0_1, :f0_2)"));
    assertEquals("a", q.params().get("f0_0"));
    assertEquals("c", q.params().get("f0_2"));
  }

  @Test
  void numericDimensionValuesAreCoercedSoIndexesStayUsable() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, null,
        List.of(new Filter("status", "gte", "500", null))));

    assertTrue(q.sql().contains("status >= :f0"));
    assertEquals(500L, q.params().get("f0"), "bound as a number, not the string '500'");
  }

  @Test
  void nonNumericValueForANumericColumnIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(null, null, List.of(new Filter("status", "eq", "abc", null)))));
    assertTrue(e.getMessage().contains("is not a number"));
  }

  @Test
  void nullChecksNeedNoValue() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, null,
        List.of(new Filter("errorPack", "notnull", null, null))));
    assertTrue(q.sql().contains("errorPack IS NOT NULL"));
  }

  @Test
  void betweenNeedsExactlyTwoValues() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(spec(null, null,
            List.of(new Filter("handlerDuration", "between", null, List.of("1"))))));
    assertTrue(e.getMessage().contains("exactly 2 values"));
  }

  @Test
  void startsWithAvoidsTheLeadingWildcardWarning() {
    BuiltQuery q = LogQueryBuilder.build(spec(null, null,
        List.of(new Filter("path", "startswith", "/folders", null))));
    assertEquals("/folders%", q.params().get("f0"));
  }

  // ---- range and limits -------------------------------------------------------------------------

  @Test
  void timeRangeIsAlwaysBoundAndDefaultsToTheLastDay() {
    BuiltQuery q = LogQueryBuilder.build(new LogQuerySpec("request", null, null, null, null, null, null, null, null));

    assertTrue(q.sql().contains("requestTime >= :from AND requestTime < :to"));
    assertEquals(24 * 60 * 60, java.time.Duration.between(q.from(), q.to()).toSeconds());
  }

  @Test
  void invertedRangeIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(new LogQuerySpec("request", TO, FROM, null, null, null, null, null, null)));
    assertTrue(e.getMessage().contains("'from' must be before 'to'"));
  }

  @Test
  void absurdRangeIsRejectedRatherThanScanningEverything() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(new LogQuerySpec("request", "2000-01-01T00:00:00Z", TO,
            null, null, null, null, null, null)));
    assertTrue(e.getMessage().contains("Range too wide"));
  }

  @Test
  void limitsAreClampedPerMode() {
    BuiltQuery raw = LogQueryBuilder.build(
        new LogQuerySpec("request", FROM, TO, null, null, null, null, 999_999, null));
    assertEquals(LogQueryBuilder.MAX_RAW_LIMIT, raw.limit());

    BuiltQuery grouped = LogQueryBuilder.build(
        new LogQuerySpec("request", FROM, TO, null, List.of("component"), List.of("count"), null, 999_999, null));
    assertEquals(LogQueryBuilder.MAX_GROUPED_LIMIT, grouped.limit());
  }

  @Test
  void unknownTableIsRejected() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(new LogQuerySpec("users", FROM, TO, null, null, null, null, null, null)));
    assertTrue(e.getMessage().contains("Unknown table 'users'"));
  }

  @Test
  void cypherTableUsesItsOwnTimeColumn() {
    BuiltQuery q = LogQueryBuilder.build(
        new LogQuerySpec("cypher", FROM, TO, null, List.of("runnableHash"), List.of("count", "p95:duration"),
            null, null, null));

    assertTrue(q.sql().contains("FROM log_cypher"));
    assertTrue(q.sql().contains("logTime >= :from"));
    assertTrue(q.sql().contains("ORDER BY `count` DESC"));
  }

  // ---- rollup source ----------------------------------------------------------------------------

  private static LogQuerySpec rollup(List<String> groupBy, List<String> metrics) {
    return new LogQuerySpec("request", FROM, TO, null, groupBy, metrics, null, null, null, null, "rollup");
  }

  @Test
  void rollupMetricsMapOntoThePreFoldedColumns() {
    BuiltQuery q = LogQueryBuilder.build(rollup(List.of("handler"),
        List.of("count", "sum:handlerDuration", "max:handlerDuration", "avg:handlerDuration")));

    assertTrue(q.sql().contains("FROM agg_request_hourly"));
    assertTrue(q.sql().contains("SUM(reqCount) AS `count`"), "a rollup row already holds a count");
    assertTrue(q.sql().contains("SUM(sumHandlerNanos) AS `sum_handlerDuration`"));
    assertTrue(q.sql().contains("MAX(maxHandlerNanos) AS `max_handlerDuration`"));
    assertTrue(q.sql().contains("SUM(sumHandlerNanos) / NULLIF(SUM(reqCount), 0)"));
    assertFalse(q.approximate(), "no percentile requested → nothing approximate");
  }

  @Test
  void rollupPercentilesComeFromTheMergedHistogramNotAWindow() {
    BuiltQuery q = LogQueryBuilder.build(rollup(List.of("component"),
        List.of("count", "p50:handlerDuration", "p95:handlerDuration")));

    assertFalse(q.sql().contains("ROW_NUMBER()"), "the rollup has no rows to rank");
    assertTrue(q.sql().contains("SUM(h0)") && q.sql().contains("SUM(h14)"), "15 merged buckets trail the select");
    assertTrue(q.approximate());
    // dims + count are SQL-backed; the two percentiles are computed from the histogram afterwards
    assertEquals(2, q.sqlValueCount());
    assertEquals(List.of(0.5, 0.95), q.percentileFractions());
    assertEquals(4, q.columns().size());
    assertTrue(q.notes().stream().anyMatch(n -> n.contains("approximate")));
  }

  @Test
  void rollupRejectsRawRowMode() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, null, null, null, null, null, null, "rollup");
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("no raw rows"));
  }

  @Test
  void rollupRejectsDistinctBecauseRowsAreGone() {
    // use a column the rollup DOES keep, so the failure is about distinct rather than the allowlist
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(rollup(List.of("component"), List.of("distinct:component"))));
    assertTrue(e.getMessage().contains("folded rows away"));
  }

  @Test
  void rollupRejectsAMinItNeverKept() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(rollup(List.of("component"), List.of("min:preHandlerDuration"))));
    assertTrue(e.getMessage().contains("only a running total"));
  }

  /**
   * Only one measure per rollup table carries a histogram, so asking for a percentile on any other
   * measure is caught by the histogram-availability check before the same-column rule can apply.
   */
  @Test
  void rollupRejectsPercentilesOnAMeasureWithNoHistogram() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(rollup(List.of("component"),
            List.of("p95:handlerDuration", "p95:preHandlerDuration"))));
    assertTrue(e.getMessage().contains("No rollup histogram for 'preHandlerDuration'"));
  }

  @Test
  void rollupHasNoMinuteGrain() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(rollup(List.of("tsMinute"), List.of("count"))));
    assertTrue(e.getMessage().contains("Unknown column 'tsMinute'"));
  }

  @Test
  void rollupDropsDimensionsTheFoldNeverKept() {
    // userId lives in agg_request_user_hourly, not the endpoint rollup — so it must not silently work
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> LogQueryBuilder.build(rollup(List.of("userId"), List.of("count"))));
    assertTrue(e.getMessage().contains("Unknown column 'userId'"));
  }

  @Test
  void sameSpecRunsAgainstEitherSource() {
    List<String> groupBy = List.of("handler");
    List<String> metrics = List.of("count", "sum:handlerDuration");
    BuiltQuery raw = LogQueryBuilder.build(spec(groupBy, metrics, null));
    BuiltQuery roll = LogQueryBuilder.build(rollup(groupBy, metrics));

    assertEquals(raw.columns().size(), roll.columns().size(), "same grammar → same shape");
    assertTrue(raw.sql().contains("FROM log_request"));
    assertTrue(roll.sql().contains("FROM agg_request_hourly"));
  }

  @Test
  void cypherRollupUsesItsOwnCountAndMeasure() {
    BuiltQuery q = LogQueryBuilder.build(new LogQuerySpec("cypher", FROM, TO, null,
        List.of("runnableHash"), List.of("count", "sum:duration", "p95:duration"),
        null, null, null, null, "rollup"));

    assertTrue(q.sql().contains("FROM agg_cypher_hourly"));
    assertTrue(q.sql().contains("SUM(execCount) AS `count`"));
    assertTrue(q.sql().contains("SUM(sumNanos) AS `sum_duration`"));
    assertTrue(q.approximate());
  }

  @Test
  void unknownSourceIsRejected() {
    LogQuerySpec s = new LogQuerySpec("request", FROM, TO, null, List.of("component"), List.of("count"),
        null, null, null, null, "warehouse");
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LogQueryBuilder.build(s));
    assertTrue(e.getMessage().contains("Unknown source 'warehouse'"));
  }

  // ---- native-query hygiene ---------------------------------------------------------------------

  /**
   * These run as Hibernate NATIVE queries, and Hibernate scans the SQL text for {@code :name} bind
   * parameters before MySQL sees it — including inside backticked aliases and inside string literals.
   * So a colon anywhere that is not a declared parameter is a latent "Named parameter not bound"
   * failure at runtime. This caught exactly that twice: metric aliases ("sum:handlerDuration") and a
   * {@code DATE_FORMAT(...,'%H:%i:00')} time bucket.
   */
  @Test
  void colonsInSqlAreOnlyBindParameters() {
    List<LogQuerySpec> specs = List.of(
        // every time bucket, both tables, percentiles, and each filter op shape
        new LogQuerySpec("request", FROM, TO, null, List.of("tsMinute"), List.of("count"), null, null, null),
        new LogQuerySpec("request", FROM, TO, null, List.of("tsHour"), List.of("count"), null, null, null),
        new LogQuerySpec("request", FROM, TO, null, List.of("tsDay"), List.of("count"), null, null, null),
        new LogQuerySpec("request", FROM, TO, null, List.of("hourOfDay", "statusClass"),
            List.of("count", "sum:handlerDuration", "p95:handlerDuration", "distinct:userId"), null, null, null),
        new LogQuerySpec("request", FROM, TO, null, List.of("pathTemplate"),
            List.of("p99:handlerDuration"), null, null, null),
        new LogQuerySpec("cypher", FROM, TO, null, List.of("tsMinute", "runnableHash"),
            List.of("count", "p50:duration", "avg:duration"), null, null, null),
        new LogQuerySpec("request", FROM, TO, List.of(
            new Filter("path", "like", "/x", null),
            new Filter("component", "in", null, List.of("a", "b")),
            new Filter("handlerDuration", "between", null, List.of("1", "2")),
            new Filter("status", "gte", "500", null),
            new Filter("errorPack", "notnull", null, null)),
            null, null, null, null, null),
        new LogQuerySpec("request", FROM, TO, null, null, null, null, null, "2026-07-30T12:00:00Z,7"),
        new LogQuerySpec("cypher", FROM, TO, null, List.of("globalRequestId", "runnableHash"),
            List.of("count", "sum:duration"), null, null, null,
            List.of(new LogQuerySpec.Having("count", "gt", "5"))));

    for (LogQuerySpec s : specs) {
      BuiltQuery q = LogQueryBuilder.build(s);
      String sql = q.sql();
      for (int i = sql.indexOf(':'); i >= 0; i = sql.indexOf(':', i + 1)) {
        int j = i + 1;
        while (j < sql.length() && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
          j++;
        }
        String name = sql.substring(i + 1, j);
        assertTrue(q.params().containsKey(name),
            "SQL contains ':" + name + "' which is not a bound parameter — Hibernate will reject this "
                + "native query. Params: " + q.params().keySet() + "\nSQL: " + sql);
      }
    }
  }

  private static int countOccurrences(String haystack, String needle) {
    int n = 0;
    for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
      n++;
    }
    return n;
  }
}
