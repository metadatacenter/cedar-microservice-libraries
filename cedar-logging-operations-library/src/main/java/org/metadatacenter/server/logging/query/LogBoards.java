package org.metadatacenter.server.logging.query;

import org.metadatacenter.server.logging.query.LogQuerySpec.Filter;
import org.metadatacenter.server.logging.query.LogQuerySpec.Having;
import org.metadatacenter.server.logging.query.LogQuerySpec.Sort;

import java.util.List;

/**
 * The Insight board catalog — the pre-defined questions, per
 * {@code cedar-development/ops/LOG-EXPLORER-UI-PLAN.md} §5.
 * <p>
 * A board is nothing but a saved {@link LogQuerySpec}: no bespoke SQL, no per-board endpoint. That is
 * the whole design — one engine, many presets — so a board is a starting point the operator can edit
 * in place rather than a dead end, and adding a question means adding a row here.
 * <p>
 * The catalog is server-owned so the UI's list cannot drift from what the engine actually supports.
 * Specs carry no {@code from}/{@code to}: the page supplies the time range, and
 * {@code defaultRangeMinutes} is only the suggested starting window.
 * <p>
 * Ranking note: boards rank by <b>total</b> time (count x duration) rather than p95 wherever the
 * question is "what should I fix". Cheap-but-constant calls dominate real systems, and a p95 ranking
 * hides them — that is how the 31-minute updateInclusionSubgraph outlier and the self-polling
 * problem were both found.
 */
public final class LogBoards {

  /**
   * The monitoring UI polls itself: queueCounts + getSummary were 87% of all logged requests on the
   * dev box. Boards about real traffic exclude those components by default, and
   * {@code noisiest-endpoints} deliberately does not, so the noise itself stays visible.
   */
  private static final List<Filter> EXCLUDE_POLLING =
      List.of(new Filter("component", "notin", null, List.of("monitor", "messaging")));

  public record Board(String id,
                      String title,
                      String question,
                      String group,
                      int defaultRangeMinutes,
                      LogQuerySpec spec,
                      String note) {
  }

  private static final int DAY = 60 * 24;
  private static final int WEEK = DAY * 7;

  private LogBoards() {
  }

  public static List<Board> all() {
    return List.of(
        // ---- requests ------------------------------------------------------------------------
        new Board("traffic-overview", "Traffic overview",
            "How much traffic, from how many users, and how slow overall?",
            "Requests", DAY,
            req(EXCLUDE_POLLING, null,
                List.of("count", "distinct:userId", "distinct:clientSessionId",
                    "p50:handlerDuration", "p95:handlerDuration", "p99:handlerDuration",
                    "max:handlerDuration"),
                null, null, 1),
            "Excludes the self-polling components."),

        new Board("traffic-per-hour", "Traffic per hour",
            "How does request volume and latency move through the day?",
            "Requests", DAY,
            req(EXCLUDE_POLLING, List.of("tsHour"),
                List.of("count", "distinct:userId", "p95:handlerDuration"),
                List.of(new Sort("tsHour", "asc")), null, 500),
            null),

        new Board("slowest-endpoints", "Slowest endpoints",
            "Which handlers cost the most time overall — the ones actually worth optimizing?",
            "Requests", WEEK,
            req(EXCLUDE_POLLING, List.of("handler", "httpMethod"),
                List.of("count", "sum:handlerDuration", "p95:handlerDuration", "max:handlerDuration"),
                List.of(new Sort("sum:handlerDuration", "desc")), null, 100),
            "Ranked by total time, not p95 — that is what surfaces cheap-but-constant calls."),

        new Board("error-hotspots", "Error hotspots",
            "Where are requests failing, and how badly?",
            "Requests", WEEK,
            req(List.of(new Filter("errorPack", "notnull", null, null)),
                List.of("handler", "statusClass"),
                List.of("count", "max:handlerDuration"),
                List.of(new Sort("count", "desc")), null, 100),
            "status is NULL before 2026-07-30, so older rows fall back to errorPack for error-ness."),

        new Board("heaviest-users", "Heaviest users & keys",
            "Who is calling the most, and through which auth path?",
            "Requests", WEEK,
            req(EXCLUDE_POLLING, List.of("userId", "authSource", "apiKeyHash"),
                List.of("count", "sum:handlerDuration", "p95:handlerDuration"),
                List.of(new Sort("count", "desc")), null, 100),
            "apiKeyHash exists only from 2026-07-30; before that a user's keys are indistinguishable."),

        new Board("by-component", "By component",
            "Which microservice carries the load, and which is slowest?",
            "Requests", DAY,
            req(null, List.of("component"),
                List.of("count", "sum:handlerDuration", "p95:handlerDuration", "max:handlerDuration",
                    "distinct:userId"),
                List.of(new Sort("count", "desc")), null, 100),
            null),

        new Board("bursts-off-hours", "Bursts & off-hours",
            "Is traffic arriving when nobody should be working?",
            "Requests", WEEK,
            req(EXCLUDE_POLLING, List.of("hourOfDay"),
                List.of("count", "distinct:userId", "p95:handlerDuration"),
                List.of(new Sort("hourOfDay", "asc")), null, 24),
            "Hour of day in the database's timezone, folded across the whole range."),

        new Board("path-templates", "Path templates",
            "Which kinds of URL cost the most, with ids collapsed away?",
            "Requests", WEEK,
            req(EXCLUDE_POLLING, List.of("pathTemplate", "httpMethod"),
                List.of("count", "sum:handlerDuration", "p95:handlerDuration"),
                List.of(new Sort("sum:handlerDuration", "desc")), null, 100),
            "UUIDs become {uuid} and long digit runs {n}, collapsing ~1,165 raw paths."),

        new Board("sessions", "Busiest sessions",
            "Which browser sessions generated the most work?",
            "Requests", DAY,
            req(EXCLUDE_POLLING, List.of("clientSessionId"),
                List.of("count", "sum:handlerDuration", "max:handlerDuration"),
                List.of(new Sort("count", "desc")), null, 100),
            "Expand a row and filter by globalRequestId to follow one request across components."),

        new Board("noisiest-endpoints", "Noisiest endpoints (log volume)",
            "What is actually filling the log table — including our own polling?",
            "Requests", DAY,
            req(null, List.of("handler", "component"),
                List.of("count"),
                List.of(new Sort("count", "desc")), null, 100),
            "Deliberately does NOT exclude polling: this is the board that shows the noise."),

        // ---- cypher --------------------------------------------------------------------------
        new Board("slow-query-shapes", "Slowest query shapes",
            "Which Cypher shapes cost the most database time in total?",
            "Cypher", WEEK,
            cypher(null, List.of("runnableHash", "operation"),
                List.of("count", "sum:duration", "p95:duration", "max:duration"),
                List.of(new Sort("sum:duration", "desc")), null, 100),
            "Expand a row for the query text; runnableHash is the md5 of the runnable form."),

        new Board("chattiest-handlers", "Chattiest handlers",
            "Which handlers issue the most database calls per request?",
            "Cypher", WEEK,
            cypher(null, List.of("handler"),
                List.of("count", "distinct:globalRequestId", "sum:duration"),
                List.of(new Sort("count", "desc")), null, 100),
            "count / distinct globalRequestId = calls per request. A high ratio is an N+1 smell."),

        new Board("n-plus-one", "N+1 detector",
            "Is one query shape being run over and over inside a single request?",
            "Cypher", WEEK,
            cypher(List.of(new Filter("globalRequestId", "notnull", null, null)),
                List.of("globalRequestId", "runnableHash"),
                List.of("count", "sum:duration", "max:duration"),
                List.of(new Sort("count", "desc")),
                List.of(new Having("count", "gt", "5")), 100),
            "Each row is one request repeating one shape more than 5 times."),

        new Board("cypher-by-operation", "Cypher by operation",
            "What mix of reads and writes is each component doing?",
            "Cypher", DAY,
            cypher(null, List.of("operation", "component"),
                List.of("count", "sum:duration", "p95:duration"),
                List.of(new Sort("count", "desc")), null, 100),
            null)
    );
  }

  public static Board byId(String id) {
    return all().stream().filter(b -> b.id().equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown board '" + id + "'."));
  }

  private static LogQuerySpec req(List<Filter> filters, List<String> groupBy, List<String> metrics,
                                  List<Sort> orderBy, List<Having> having, int limit) {
    return new LogQuerySpec(LogQueryColumns.T_REQUEST, null, null, filters, groupBy, metrics,
        orderBy, limit, null, having);
  }

  private static LogQuerySpec cypher(List<Filter> filters, List<String> groupBy, List<String> metrics,
                                     List<Sort> orderBy, List<Having> having, int limit) {
    return new LogQuerySpec(LogQueryColumns.T_CYPHER, null, null, filters, groupBy, metrics,
        orderBy, limit, null, having);
  }
}
