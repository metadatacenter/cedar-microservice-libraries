package org.metadatacenter.server.logging.dao.agg;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.metadatacenter.server.logging.dbmodel.agg.LogAggregationState;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

public class LogAggregationStateDAO extends AbstractDAO<LogAggregationState> {

  public LogAggregationStateDAO(SessionFactory factory) {
    super(factory);
  }

  public LogAggregationState find(String sourceTable, String bucket) {
    CriteriaBuilder cb = currentSession().getCriteriaBuilder();
    CriteriaQuery<LogAggregationState> q = cb.createQuery(LogAggregationState.class);
    Root<LogAggregationState> root = q.from(LogAggregationState.class);
    q.select(root).where(
        cb.and(cb.equal(root.get("sourceTable"), sourceTable), cb.equal(root.get("bucket"), bucket)));
    Query<LogAggregationState> query = currentSession().createQuery(q);
    return query.uniqueResult();
  }

  /** Find, or create-and-persist, the state row for this (sourceTable, bucket). */
  public LogAggregationState findOrCreate(String sourceTable, String bucket) {
    LogAggregationState s = find(sourceTable, bucket);
    if (s == null) {
      s = new LogAggregationState();
      s.setSourceTable(sourceTable);
      s.setBucket(bucket);
      s.setStatus(LogAggregationState.Status.PENDING);
      persist(s);
    }
    return s;
  }

  public LogAggregationState save(LogAggregationState s) {
    return persist(s);
  }
}
