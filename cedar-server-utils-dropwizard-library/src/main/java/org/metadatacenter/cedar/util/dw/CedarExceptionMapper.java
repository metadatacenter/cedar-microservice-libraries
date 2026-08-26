package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.filter.LoggingContext;
import org.metadatacenter.server.logging.filter.ThreadLocalRequestIdHolder;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.util.http.CedarResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class CedarExceptionMapper extends AbstractExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger log = LoggerFactory.getLogger(CedarCedarExceptionMapper.class);

  public Response toResponse(Exception exception) {

    log.warn(":CEM::", exception);
    if (exception instanceof BadRequestException) {
      return CedarResponse.badRequest().build();
    } else if (exception instanceof ForbiddenException) {
      return CedarResponse.forbidden().build();
    } else if (exception instanceof NotAcceptableException) {
      return CedarResponse.notAcceptable().build();
    } else if (exception instanceof NotAllowedException) {
      return CedarResponse.methodNotAllowed().build();
    } else if (exception instanceof NotAuthorizedException) {
      return CedarResponse.unauthorized().build();
    } else if (exception instanceof NotFoundException) {
      return CedarResponse.notFound().build();
    } else if (exception instanceof NotSupportedException) {
      // JAX-RS throws NotSupportedException when the request's Content-Type does not match the
      // endpoint's @Consumes, which is 415 Unsupported Media Type. It has nothing to do with the
      // HTTP protocol version: this previously answered 505, reporting a client mistake as a
      // server fault (and as a retryable 5xx).
      return CedarResponse.unsupportedMediaType().build();
    } else if (exception instanceof WebApplicationException webApplicationException) {
      // Any other framework-level rejection Jersey raises before the resource runs — most visibly a
      // ParamException when a query param cannot be parsed into its type, such as a non-integer
      // limit=abc, which Jersey classifies as 400. Honor the status Jersey chose; the fallthrough
      // below would otherwise report every one of these as a 500.
      int status = webApplicationException.getResponse().getStatus();
      return Response.status(status).build();
    }

    LoggingContext loggingContext = ThreadLocalRequestIdHolder.getLoggingContext();
    String globalRequestId = null;
    String localRequestId = null;
    if (loggingContext != null) {
      globalRequestId = loggingContext.getGlobalRequestId();
      localRequestId = loggingContext.getLocalRequestId();
    }

    CedarErrorPack errorPack = new CedarErrorPack();
    errorPack.sourceException(exception);

    AppLogger.message(AppLogType.RESPONSE_EXCEPTION, AppLogSubType.START, globalRequestId, localRequestId)
        .param(AppLogParam.EXCEPTION, errorPack)
        .enqueue();

    return Response.status(CedarResponseStatus.INTERNAL_SERVER_ERROR.getStatusCode())
        .entity(clientSafeCopy(errorPack))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

}
