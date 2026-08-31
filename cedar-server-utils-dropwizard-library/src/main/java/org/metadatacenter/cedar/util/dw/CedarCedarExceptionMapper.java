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

import org.metadatacenter.constant.HttpConstants;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

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

    AppLogger.message(AppLogType.RESPONSE_EXCEPTION, AppLogSubType.START, globalRequestId, localRequestId)
        .param(AppLogParam.EXCEPTION, errorPack)
        .enqueue();

    int statusCode = errorPack.getStatus().getStatusCode();
    logMappedException(log, ":CCEM:", exception, statusCode, exception.isShowFullStackTrace());
    Response.ResponseBuilder responseBuilder = Response.status(statusCode)
        .entity(clientSafeCopy(errorPack))
        .type(MediaType.APPLICATION_JSON);
    if (statusCode == Response.Status.UNAUTHORIZED.getStatusCode()) {
      // This mapper builds its own response rather than going through CedarResponse, and it is the
      // path an unauthenticated request takes: buildRequestContext throws, and the CedarException
      // arrives here. Without this the most common 401 in the system carries no challenge.
      responseBuilder.header(HttpHeaders.WWW_AUTHENTICATE, HttpConstants.HTTP_AUTH_CHALLENGE);
    }
    return responseBuilder.build();
  }

}
