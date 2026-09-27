package org.metadatacenter.server.logging.dao.agg;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.metadatacenter.server.logging.agg.LogExplorerResults.CypherRow;
import org.metadatacenter.server.logging.agg.LogExplorerResults.RequestRow;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Row-level reads over the RAW log tables for the Live Explorer (newest first). Free-text filter is a
 * LIKE across the human-searchable columns; minDurationNanos filters slow calls. Methods assume an
 * active session (the resource is {@code @UnitOfWork}).
 */
public class LogExplorerDAO extends AbstractDAO<ApplicationRequestLog> {

  public LogExplorerDAO(SessionFactory factory) {
    super(factory);
  }

  public List<RequestRow> recentRequests(String q, long minDurationNanos, int limit) {
    return recentRequests(q, minDurationNanos, limit, 0);
  }

  /**
   * A page of the raw request log, newest first. The identity column breaks ties between rows logged in
   * the same instant, so consecutive pages neither repeat nor skip a row.
   */
  public List<RequestRow> recentRequests(String q, long minDurationNanos, int limit, int offset) {
    Filter filter = requestFilter(q, minDurationNanos);
    NativeQuery<?> query = currentSession().createNativeQuery(
        "SELECT globalRequestId, requestTime, systemComponentName, httpMethod, path, className, methodName, "
            + "userId, authSource, apiKeyHash, status, handlerDuration, errorPack FROM log_request"
            + filter.where() + " ORDER BY requestTime DESC, id DESC LIMIT :lim OFFSET :off");
    filter.bind(query);
    query.setParameter("lim", limit);
    query.setParameter("off", offset);

    List<RequestRow> out = new ArrayList<>();
    for (Object r : query.getResultList()) {
      Object[] c = (Object[]) r;
      String cls = str(c[5]);
      String mth = str(c[6]);
      out.add(new RequestRow(str(c[0]), iso(c[1]), str(c[2]), str(c[3]), str(c[4]),
          handler(cls, mth), str(c[7]), str(c[8]), str(c[9]), intOrNull(c[10]), num(c[11]), str(c[12])));
    }
    return out;
  }

  /**
   * How many raw request rows match, counted no further than {@code cap}. The raw log holds tens of
   * millions of rows and the free-text filter is a substring match, so an exact count would scan the
   * table on every page; counting stops once {@code cap} matches are found.
   */
  public long countRequests(String q, long minDurationNanos, long cap) {
    Filter filter = requestFilter(q, minDurationNanos);
    return cappedCount("SELECT 1 FROM log_request" + filter.where(), filter, cap);
  }

  public List<CypherRow> recentCypher(String q, long minDurationNanos, int limit) {
    return recentCypher(q, minDurationNanos, limit, 0);
  }

  /** A page of the raw Cypher log, newest first, with the identity column breaking ties. */
  public List<CypherRow> recentCypher(String q, long minDurationNanos, int limit, int offset) {
    Filter filter = cypherFilter(q, minDurationNanos);
    NativeQuery<?> query = currentSession().createNativeQuery(
        "SELECT logTime, systemComponentName, operation, runnableHash, duration, runnable, parameters, "
            + "className, methodName FROM log_cypher"
            + filter.where() + " ORDER BY logTime DESC, id DESC LIMIT :lim OFFSET :off");
    filter.bind(query);
    query.setParameter("lim", limit);
    query.setParameter("off", offset);

    List<CypherRow> out = new ArrayList<>();
    for (Object r : query.getResultList()) {
      Object[] c = (Object[]) r;
      out.add(new CypherRow(iso(c[0]), str(c[1]), str(c[2]), str(c[3]), num(c[4]), str(c[5]),
          str(c[6]), handler(str(c[7]), str(c[8]))));
    }
    return out;
  }

  /** How many raw Cypher rows match, counted no further than {@code cap}, for the reason on {@link #countRequests}. */
  public long countCypher(String q, long minDurationNanos, long cap) {
    Filter filter = cypherFilter(q, minDurationNanos);
    return cappedCount("SELECT 1 FROM log_cypher" + filter.where(), filter, cap);
  }

  public List<RequestRow> requestOutliers(String kind, int limit) {
    return requestOutliers(kind, limit, 0);
  }

  /** A page of the retained slowest and failed request instances (kept forever), slowest first. */
  public List<RequestRow> requestOutliers(String kind, int limit, int offset) {
    Filter filter = outlierFilter(kind);
    NativeQuery<?> q = currentSession().createNativeQuery(
        "SELECT requestTime, systemComponentName, httpMethod, path, className, methodName, userId, "
            + "authSource, apiKeyHash, status, durationNanos, errorPack FROM agg_request_outlier"
            + filter.where() + " ORDER BY durationNanos DESC, id DESC LIMIT :lim OFFSET :off");
    filter.bind(q);
    q.setParameter("lim", limit);
    q.setParameter("off", offset);
    List<RequestRow> out = new ArrayList<>();
    for (Object r : q.getResultList()) {
      Object[] c = (Object[]) r;
      out.add(new RequestRow(null, iso(c[0]), str(c[1]), str(c[2]), str(c[3]), handler(str(c[4]), str(c[5])),
          str(c[6]), str(c[7]), str(c[8]), intOrNull(c[9]), num(c[10]), str(c[11])));
    }
    return out;
  }

  /** How many retained request outliers match. The outlier tables are bounded, so the count is exact. */
  public long countRequestOutliers(String kind) {
    Filter filter = outlierFilter(kind);
    NativeQuery<?> q = currentSession().createNativeQuery(
        "SELECT COUNT(*) FROM agg_request_outlier" + filter.where());
    filter.bind(q);
    return num(q.getSingleResult());
  }

  public List<CypherRow> cypherOutliers(int limit) {
    return cypherOutliers(limit, 0);
  }

  /** A page of the retained slowest Cypher instances (kept forever), slowest first. */
  public List<CypherRow> cypherOutliers(int limit, int offset) {
    NativeQuery<?> q = currentSession().createNativeQuery(
        "SELECT logTime, systemComponentName, operation, runnableHash, durationNanos, runnable, parameters, "
            + "className, methodName FROM agg_cypher_outlier ORDER BY durationNanos DESC, id DESC "
            + "LIMIT :lim OFFSET :off");
    q.setParameter("lim", limit);
    q.setParameter("off", offset);
    List<CypherRow> out = new ArrayList<>();
    for (Object r : q.getResultList()) {
      Object[] c = (Object[]) r;
      out.add(new CypherRow(iso(c[0]), str(c[1]), str(c[2]), str(c[3]), num(c[4]), str(c[5]), str(c[6]),
          handler(str(c[7]), str(c[8]))));
    }
    return out;
  }

  /** How many retained Cypher outliers there are. */
  public long countCypherOutliers() {
    return num(currentSession().createNativeQuery("SELECT COUNT(*) FROM agg_cypher_outlier").getSingleResult());
  }

  private long cappedCount(String matchingRows, Filter filter, long cap) {
    NativeQuery<?> q = currentSession().createNativeQuery(
        "SELECT COUNT(*) FROM (" + matchingRows + " LIMIT :cap) capped");
    filter.bind(q);
    q.setParameter("cap", cap);
    return num(q.getSingleResult());
  }

  private static Filter requestFilter(String q, long minDurationNanos) {
    Filter filter = new Filter();
    if (q != null && !q.isBlank()) {
      filter.add("(path LIKE :q OR userId LIKE :q OR className LIKE :q OR globalRequestId LIKE :q)",
          "q", "%" + q.trim() + "%");
    }
    if (minDurationNanos > 0) {
      filter.add("handlerDuration >= :minDur", "minDur", minDurationNanos);
    }
    return filter;
  }

  private static Filter cypherFilter(String q, long minDurationNanos) {
    Filter filter = new Filter();
    if (q != null && !q.isBlank()) {
      filter.add("(operation LIKE :q OR runnableHash LIKE :q OR className LIKE :q OR runnable LIKE :q)",
          "q", "%" + q.trim() + "%");
    }
    if (minDurationNanos > 0) {
      filter.add("duration >= :minDur", "minDur", minDurationNanos);
    }
    return filter;
  }

  private static Filter outlierFilter(String kind) {
    Filter filter = new Filter();
    if (kind != null && !kind.isBlank()) {
      filter.add("kind = :kind", "kind", kind.trim().toUpperCase());
    }
    return filter;
  }

  /** The conditions and bound values one listing shares between its page query and its count. */
  private static final class Filter {
    private final List<String> conditions = new ArrayList<>();
    private final Map<String, Object> parameters = new LinkedHashMap<>();

    void add(String condition, String name, Object value) {
      conditions.add(condition);
      parameters.put(name, value);
    }

    String where() {
      return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    void bind(NativeQuery<?> query) {
      parameters.forEach(query::setParameter);
    }
  }

  private static String handler(String cls, String mth) {
    if (cls == null && mth == null) {
      return null;
    }
    return (cls == null ? "?" : cls) + "." + (mth == null ? "?" : mth) + "()";
  }

  private static long num(Object o) {
    return o == null ? 0L : ((Number) o).longValue();
  }

  private static Integer intOrNull(Object o) {
    return o == null ? null : ((Number) o).intValue();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static String iso(Object o) {
    if (o instanceof Timestamp t) {
      return t.toInstant().toString();
    }
    if (o instanceof Instant i) {
      return i.toString();
    }
    return o == null ? null : o.toString();
  }
}
