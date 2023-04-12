package org.metadatacenter.server.logging.dao;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

public class ApplicationRequestLogDAO extends AbstractDAO<ApplicationRequestLog> {

  public ApplicationRequestLogDAO(SessionFactory factory) {
    super(factory);
  }

  public Long createOrUpdate(ApplicationRequestLog log) {
    return persist(log).getId();
  }

  public ApplicationRequestLog findByLocalRequestId(String localRequestId) {
    CriteriaBuilder builder = currentSession().getCriteriaBuilder();
    CriteriaQuery<ApplicationRequestLog> query = builder.createQuery(ApplicationRequestLog.class);
    Root<ApplicationRequestLog> root = query.from(ApplicationRequestLog.class);
    query.select(root);
    query.where(builder.equal(root.get("localRequestId"), localRequestId));
    Query<ApplicationRequestLog> q = currentSession().createQuery(query);
    return q.uniqueResult();
  }
}