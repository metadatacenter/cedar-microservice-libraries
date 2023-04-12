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

public class AppLoggerExecutorService {

  private static final Logger log = LoggerFactory.getLogger(AppLoggerExecutorService.class);
  private ApplicationRequestLogDAO requestLogDAO;
  private ApplicationCypherLogDAO cypherLogDAO;

  public AppLoggerExecutorService(ApplicationRequestLogDAO requestLogDAO,
                                  ApplicationCypherLogDAO cypherLogDAO) {
    this.requestLogDAO = requestLogDAO;
    this.cypherLogDAO = cypherLogDAO;
  }

  @UnitOfWork
  public void handleLog(AppLogMessage appLog) {
    if (appLog.getType() == AppLogType.REQUEST_FILTER) {
      if (appLog.getSubType() == AppLogSubType.START) {
        ApplicationRequestLog l = ApplicationRequestLog.fromAppRequestFilter(appLog);
        requestLogDAO.createOrUpdate(l);
      } else if (appLog.getSubType() == AppLogSubType.END) {
        ApplicationRequestLog oldLog = requestLogDAO.findByLocalRequestId(appLog.getLocalRequestId());
        if (oldLog != null) {
          oldLog.mergeEndLog(appLog);
          requestLogDAO.createOrUpdate(oldLog);
        }
      }
    } else if (appLog.getType() == AppLogType.REQUEST_HANDLER) {
      ApplicationRequestLog oldLog = requestLogDAO.findByLocalRequestId(appLog.getLocalRequestId());
      if (oldLog != null) {
        oldLog.mergeStartLog(appLog);
        requestLogDAO.createOrUpdate(oldLog);
      }
    } else if (appLog.getType() == AppLogType.RESPONSE_EXCEPTION) {
      ApplicationRequestLog oldLog = requestLogDAO.findByLocalRequestId(appLog.getLocalRequestId());
      if (oldLog != null) {
        try {
          oldLog.setErrorPack(JsonMapper.MAPPER.writeValueAsString(appLog.getParamAsMap(AppLogParam.EXCEPTION)));
          requestLogDAO.createOrUpdate(oldLog);
        } catch (JsonProcessingException e) {
          log.error("Error while serializing ErrorPack for DB log", e);
        }
      }
    } else if (appLog.getType() == AppLogType.CYPHER_QUERY) {
      ApplicationCypherLog l = ApplicationCypherLog.fromAppCypherLog(appLog);
      cypherLogDAO.create(l);
    }
  }
}
