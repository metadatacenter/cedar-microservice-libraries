package org.metadatacenter.server.logging.dao.agg;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.metadatacenter.server.logging.agg.AggQueryResults.CypherStat;
import org.metadatacenter.server.logging.agg.AggQueryResults.EndpointStat;
import org.metadatacenter.server.logging.agg.AggQueryResults.TimeBucket;
import org.metadatacenter.server.logging.agg.AggQueryResults.UsageTotals;
import org.metadatacenter.server.logging.agg.AggQueryResults.UserStat;
import org.metadatacenter.server.logging.agg.LatencyHistogram;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestHourly;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-side queries over the hourly rollups. A date range is answered by SUMming the covered hour rows
 * (and the histogram bucket columns), then computing percentiles from the merged histogram in Java —
 * the merge-then-percentile contract. Timezone-day/week reports are just different [from,to) bounds the
 * caller supplies. Methods assume an active session (the resource is {@code @UnitOfWork}).
 */
public class AggregationQueryDAO extends AbstractDAO<AggRequestHourly> {

  private static final int NB = LatencyHistogram.BUCKETS;
  private static final String SUM_H = sumH();          // "SUM(h0), SUM(h1), ... SUM(h14)"

  public AggregationQueryDAO(SessionFactory factory) {
    super(factory);
  }

  private static String sumH() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < NB; i++) {
      sb.append(i == 0 ? "" : ", ").append("SUM(h").append(i).append(")");
    }
    return sb.toString();
  }

  // ---- endpoints ---------------------------------------------------------------------------------

  public List<EndpointStat> endpointBreakdown(Instant from, Instant to, int limit) {
    String sql = "SELECT systemComponentName, className, methodName, httpMethod, "
        + "SUM(reqCount), SUM(errorCount), MAX(maxHandlerNanos), " + SUM_H + " "
        + "FROM agg_request_hourly WHERE hourUtc >= :from AND hourUtc < :to "
        + "GROUP BY systemComponentName, className, methodName, httpMethod "
        + "ORDER BY SUM(reqCount) DESC LIMIT :lim";
    List<EndpointStat> out = new ArrayList<>();
    for (Object[] r : rows(sql, from, to, limit)) {
      long[] h = readHist(r, 7);
      out.add(new EndpointStat(str(r[0]), str(r[1]), str(r[2]), str(r[3]), num(r[4]), num(r[5]),
          LatencyHistogram.percentileNanos(h, 0.50), LatencyHistogram.percentileNanos(h, 0.95),
          LatencyHistogram.percentileNanos(h, 0.99), num(r[6])));
    }
    return out;
  }

  // ---- cypher ------------------------------------------------------------------------------------

  public List<CypherStat> cypherBreakdown(Instant from, Instant to, int limit) {
    String sql = "SELECT c.operation, c.runnableHash, SUM(c.execCount), MAX(c.maxNanos), "
        + sumHPrefixed("c") + ", ANY_VALUE(LEFT(cat.runnableSample, 2000)) "
        + "FROM agg_cypher_hourly c LEFT JOIN agg_cypher_query_catalog cat ON cat.runnableHash = c.runnableHash "
        + "WHERE c.hourUtc >= :from AND c.hourUtc < :to "
        + "GROUP BY c.operation, c.runnableHash ORDER BY SUM(c.execCount) DESC LIMIT :lim";
    List<CypherStat> out = new ArrayList<>();
    for (Object[] r : rows(sql, from, to, limit)) {
      long[] h = readHist(r, 4);
      String sample = str(r[4 + NB]);
      out.add(new CypherStat(str(r[0]), str(r[1]), sample, num(r[2]),
          LatencyHistogram.percentileNanos(h, 0.50), LatencyHistogram.percentileNanos(h, 0.95),
          LatencyHistogram.percentileNanos(h, 0.99), num(r[3])));
    }
    return out;
  }

  // ---- users / keys ------------------------------------------------------------------------------

  public List<UserStat> userBreakdown(Instant from, Instant to, int limit) {
    String sql = "SELECT userId, authSource, apiKeyHash, SUM(reqCount), SUM(errorCount) "
        + "FROM agg_request_user_hourly WHERE hourUtc >= :from AND hourUtc < :to "
        + "GROUP BY userId, authSource, apiKeyHash ORDER BY SUM(reqCount) DESC LIMIT :lim";
    List<UserStat> out = new ArrayList<>();
    for (Object[] r : rows(sql, from, to, limit)) {
      out.add(new UserStat(str(r[0]), str(r[1]), str(r[2]), num(r[3]), num(r[4])));
    }
    return out;
  }

  // ---- volume series -----------------------------------------------------------------------------

  public List<TimeBucket> volumeSeries(Instant from, Instant to) {
    String sql = "SELECT hourUtc, SUM(reqCount), SUM(errorCount) FROM agg_request_hourly "
        + "WHERE hourUtc >= :from AND hourUtc < :to GROUP BY hourUtc ORDER BY hourUtc";
    List<TimeBucket> out = new ArrayList<>();
    for (Object[] r : rows(sql, from, to, Integer.MAX_VALUE)) {
      out.add(new TimeBucket(String.valueOf(ts(r[0])), num(r[1]), num(r[2])));
    }
    return out;
  }

  // ---- totals ------------------------------------------------------------------------------------

  public UsageTotals totals(Instant from, Instant to) {
    String sql = "SELECT SUM(reqCount), SUM(errorCount), " + SUM_H
        + " FROM agg_request_hourly WHERE hourUtc >= :from AND hourUtc < :to";
    List<Object[]> rows = rows(sql, from, to, 1);
    if (rows.isEmpty() || rows.get(0)[0] == null) {
      return new UsageTotals(0, 0, 0, 0, 0);
    }
    Object[] r = rows.get(0);
    long[] h = readHist(r, 2);
    return new UsageTotals(num(r[0]), num(r[1]),
        LatencyHistogram.percentileNanos(h, 0.50), LatencyHistogram.percentileNanos(h, 0.95),
        LatencyHistogram.percentileNanos(h, 0.99));
  }

  // ---- helpers -----------------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private List<Object[]> rows(String sql, Instant from, Instant to, int limit) {
    var q = currentSession().createNativeQuery(sql)
        .setParameter("from", Timestamp.from(from)).setParameter("to", Timestamp.from(to));
    if (sql.contains(":lim")) {
      q.setParameter("lim", limit);
    }
    return q.getResultList();
  }

  private static String sumHPrefixed(String alias) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < NB; i++) {
      sb.append(i == 0 ? "" : ", ").append("SUM(").append(alias).append(".h").append(i).append(")");
    }
    return sb.toString();
  }

  private static long[] readHist(Object[] r, int offset) {
    long[] h = new long[NB];
    for (int i = 0; i < NB; i++) {
      h[i] = num(r[offset + i]);
    }
    return h;
  }

  private static long num(Object o) {
    return o == null ? 0L : ((Number) o).longValue();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static Instant ts(Object o) {
    if (o instanceof Timestamp t) {
      return t.toInstant();
    }
    if (o instanceof Instant i) {
      return i;
    }
    return null;
  }
}
