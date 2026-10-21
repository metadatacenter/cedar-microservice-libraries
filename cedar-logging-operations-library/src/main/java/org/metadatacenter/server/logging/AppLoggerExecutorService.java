package org.metadatacenter.server.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dropwizard.hibernate.UnitOfWork;
import org.metadatacenter.server.logging.dao.ApplicationCypherLogDAO;
import org.metadatacenter.server.logging.dao.ApplicationRequestLogDAO;
import org.metadatacenter.server.logging.dbmodel.ApplicationCypherLog;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class AppLoggerExecutorService {

  private static final Logger log = LoggerFactory.getLogger(AppLoggerExecutorService.class);

  private ApplicationRequestLogDAO requestLogDAO;
  private ApplicationCypherLogDAO cypherLogDAO;
  private final AtomicLong lateFragments = new AtomicLong();

  public AppLoggerExecutorService(ApplicationRequestLogDAO requestLogDAO,
                                  ApplicationCypherLogDAO cypherLogDAO) {
    this.requestLogDAO = requestLogDAO;
    this.cypherLogDAO = cypherLogDAO;
  }

  @UnitOfWork
  public void handleLog(AppLogMessage appLog) {
    var receipt = requestLogDAO.claimEvent(appLog);
    if (receipt.isProcessed(appLog)) return;
    if (appLog.getType() == AppLogType.CYPHER_QUERY) {
      cypherLogDAO.create(ApplicationCypherLog.fromAppCypherLog(appLog));
    } else {
      ApplicationRequestLog row = requestLogDAO.lockOrCreate(appLog.getLocalRequestId(), receipt.hasRequestFragments());
      if (row == null) {
        receipt.markProcessed(appLog);
        return;
      }
      boolean wasAggregated = row.wasAggregated();
      boolean changed = false;
      if (appLog.getType() == AppLogType.REQUEST_FILTER && appLog.getSubType() == AppLogSubType.START) {
        changed = row.mergeFilterStartLog(appLog);
      } else if (appLog.getType() == AppLogType.REQUEST_FILTER && appLog.getSubType() == AppLogSubType.END) {
        changed = row.mergeEndLog(appLog);
      } else if (appLog.getType() == AppLogType.REQUEST_HANDLER) {
        changed = row.mergeStartLog(appLog);
      } else if (appLog.getType() == AppLogType.RESPONSE_EXCEPTION) {
        try {
          row.setErrorPack(JsonMapper.STRICT_MAPPER.writeValueAsString(appLog.getParamAsMap(AppLogParam.EXCEPTION)));
          changed = true;
        } catch (JsonProcessingException e) {
          throw new AppLogNotRetryableException("Cannot serialize log exception", e);
        }
      }
      if (changed && wasAggregated) {
        // The live aggregator folds a request once both filters have logged it, so the handler's START fragment
        // or an exception can arrive after its row was folded, typically when a dead-lettered fragment is replayed.
        // The raw row takes the fragment, the rollups keep the request as it was folded: its count, status and
        // hour are right; its handler class, user, handler time and, without a status, its error flag are not.
        // Refusing the fragment (2.9.25) could never succeed and only parked it in the dead-letter queue.
        long total = lateFragments.incrementAndGet();
        log.warn("A late {}/{} fragment of request {} was merged after the request was aggregated; its rollups "
            + "do not include it ({} since start)", appLog.getType().getValue(), appLog.getSubType().getValue(),
            appLog.getLocalRequestId(), total);
      }
    }
    // Dirty checking writes both the request fragment and its receipt in this UnitOfWork.
    receipt.markProcessed(appLog);
  }

  /** Fragments merged into a request after it was aggregated, since this service started. */
  public long getLateFragmentCount() {
    return lateFragments.get();
  }
}
