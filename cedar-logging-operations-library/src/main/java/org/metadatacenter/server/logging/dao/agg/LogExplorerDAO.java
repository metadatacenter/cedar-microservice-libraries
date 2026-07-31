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
import java.util.List;

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
    StringBuilder sql = new StringBuilder(
        "SELECT globalRequestId, requestTime, systemComponentName, httpMethod, path, className, methodName, "
            + "userId, authSource, apiKeyHash, status, handlerDuration, errorPack FROM log_request WHERE 1=1");
    boolean hasQ = q != null && !q.isBlank();
    if (hasQ) {
      sql.append(" AND (path LIKE :q OR userId LIKE :q OR className LIKE :q OR globalRequestId LIKE :q)");
    }
    if (minDurationNanos > 0) {
      sql.append(" AND handlerDuration >= :minDur");
    }
    sql.append(" ORDER BY requestTime DESC LIMIT :lim");

    NativeQuery<?> query = currentSession().createNativeQuery(sql.toString());
    if (hasQ) {
      query.setParameter("q", "%" + q.trim() + "%");
    }
    if (minDurationNanos > 0) {
      query.setParameter("minDur", minDurationNanos);
    }
    query.setParameter("lim", limit);

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

  public List<CypherRow> recentCypher(String q, long minDurationNanos, int limit) {
    StringBuilder sql = new StringBuilder(
        "SELECT logTime, systemComponentName, operation, runnableHash, duration, runnable, parameters, "
            + "className, methodName FROM log_cypher WHERE 1=1");
    boolean hasQ = q != null && !q.isBlank();
    if (hasQ) {
      sql.append(" AND (operation LIKE :q OR runnableHash LIKE :q OR className LIKE :q OR runnable LIKE :q)");
    }
    if (minDurationNanos > 0) {
      sql.append(" AND duration >= :minDur");
    }
    sql.append(" ORDER BY logTime DESC LIMIT :lim");

    NativeQuery<?> query = currentSession().createNativeQuery(sql.toString());
    if (hasQ) {
      query.setParameter("q", "%" + q.trim() + "%");
    }
    if (minDurationNanos > 0) {
      query.setParameter("minDur", minDurationNanos);
    }
    query.setParameter("lim", limit);

    List<CypherRow> out = new ArrayList<>();
    for (Object r : query.getResultList()) {
      Object[] c = (Object[]) r;
      out.add(new CypherRow(iso(c[0]), str(c[1]), str(c[2]), str(c[3]), num(c[4]), str(c[5]),
          str(c[6]), handler(str(c[7]), str(c[8]))));
    }
    return out;
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
