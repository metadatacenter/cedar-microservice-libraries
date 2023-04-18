package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.filter.LoggingContext;
import org.metadatacenter.server.logging.filter.ThreadLocalRequestIdHolder;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class CedarCedarExceptionMapper extends AbstractExceptionMapper implements ExceptionMapper<CedarException> {

  private static final Logger log = LoggerFactory.getLogger(CedarCedarExceptionMapper.class);

  public Response toResponse(CedarException exception) {

    LoggingContext loggingContext = ThreadLocalRequestIdHolder.getLoggingContext();
    String globalRequestId = null;
    String localRequestId = null;
    if (loggingContext != null) {
      globalRequestId = loggingContext.getGlobalRequestId();
      localRequestId = loggingContext.getLocalRequestId();
    }

    CedarErrorPack errorPack = exception.getErrorPack();
    hideExceptionConditionally(errorPack);

    AppLogger.message(AppLogType.RESPONSE_EXCEPTION, AppLogSubType.START, globalRequestId, localRequestId)
        .param(AppLogParam.EXCEPTION, errorPack)
        .enqueue();

    if (exception.isShowFullStackTrace()) {
      log.warn(":CCEM:full:", exception);
    } else {
      log.warn(":CCEM:msg :" + exception.getMessage());
    }
    return Response.status(errorPack.getStatus().getStatusCode())
        .entity(errorPack)
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

}
