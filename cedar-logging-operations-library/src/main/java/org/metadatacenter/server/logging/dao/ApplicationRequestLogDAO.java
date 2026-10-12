package org.metadatacenter.server.logging.dao;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;
import org.metadatacenter.server.logging.dbmodel.ApplicationLogReceipt;
import org.metadatacenter.server.logging.model.AppLogIdentity;
import org.metadatacenter.server.logging.model.AppLogMessage;
import jakarta.persistence.LockModeType;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

public class ApplicationRequestLogDAO extends AbstractDAO<ApplicationRequestLog> {

  public ApplicationRequestLogDAO(SessionFactory factory) {
    super(factory);
  }

  /** Upsert obtains a database lock even when another consumer is inserting the same identity. */
  public ApplicationLogReceipt claimEvent(AppLogMessage message) {
    String key = AppLogIdentity.key(message);
    currentSession().createNativeMutationQuery("INSERT INTO log_event_receipt (eventKey, processed, requestPhases) VALUES (:key, false, 0) "
        + "ON DUPLICATE KEY UPDATE eventKey = eventKey").setParameter("key", key).executeUpdate();
    return currentSession().find(ApplicationLogReceipt.class, key, LockModeType.PESSIMISTIC_WRITE);
  }

  /** A fragment can arrive first; all writers serialize on the same skeleton row. */
  public ApplicationRequestLog lockOrCreate(String localRequestId, boolean alreadyCreated) {
    // claimEvent owns the request receipt lock. Once its raw row was pruned, a newly arriving
    // exception may acknowledge its phase but must not create an incomplete replacement row.
    if (!alreadyCreated) {
      currentSession().createNativeMutationQuery("INSERT INTO log_request "
          + "(localRequestId, handlerDuration, preHandlerDuration, lineNumber) VALUES (:id, 0, 0, 0) "
          + "ON DUPLICATE KEY UPDATE id = id").setParameter("id", localRequestId).executeUpdate();
    }
    return currentSession().createQuery("FROM ApplicationRequestLog WHERE localRequestId = :id", ApplicationRequestLog.class)
        .setParameter("id", localRequestId).setLockMode(LockModeType.PESSIMISTIC_WRITE).uniqueResult();
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
