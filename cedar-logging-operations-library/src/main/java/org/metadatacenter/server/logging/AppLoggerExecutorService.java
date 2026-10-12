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

public class AppLoggerExecutorService {

  private ApplicationRequestLogDAO requestLogDAO;
  private ApplicationCypherLogDAO cypherLogDAO;

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
      if (appLog.getType() == AppLogType.REQUEST_FILTER && appLog.getSubType() == AppLogSubType.START) {
        row.mergeFilterStartLog(appLog);
      } else if (appLog.getType() == AppLogType.REQUEST_FILTER && appLog.getSubType() == AppLogSubType.END) {
        row.mergeEndLog(appLog);
      } else if (appLog.getType() == AppLogType.REQUEST_HANDLER) {
        row.mergeStartLog(appLog);
      } else if (appLog.getType() == AppLogType.RESPONSE_EXCEPTION) {
        try {
          row.setErrorPack(JsonMapper.STRICT_MAPPER.writeValueAsString(appLog.getParamAsMap(AppLogParam.EXCEPTION)));
        } catch (JsonProcessingException e) {
          throw new IllegalArgumentException("Cannot serialize log exception", e);
        }
      }
    }
    // Dirty checking writes both the request fragment and its receipt in this UnitOfWork.
    receipt.markProcessed(appLog);
  }
}
