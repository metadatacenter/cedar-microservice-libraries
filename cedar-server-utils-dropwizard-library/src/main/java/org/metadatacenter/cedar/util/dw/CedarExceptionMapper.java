package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.filter.LoggingContext;
import org.metadatacenter.server.logging.filter.ThreadLocalRequestIdHolder;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.util.http.CedarResponse;
import org.metadatacenter.util.http.CedarError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;

@Provider
public class CedarExceptionMapper extends AbstractExceptionMapper implements ExceptionMapper<Exception> {

  private static final Logger log = LoggerFactory.getLogger(CedarExceptionMapper.class);

  public Response toResponse(Exception exception) {

    if (exception instanceof org.metadatacenter.cedar.util.dw.ratelimit.UserRateLimitException quota) {
      return quota.getResponse();
    }

    if (isNeo4jUnavailable(exception)) {
      return new CedarCedarExceptionMapper().toResponse(
          new CedarDependencyUnavailableException("Neo4j is unavailable", exception));
    } else if (isMongoUnavailable(exception)) {
      return new CedarCedarExceptionMapper().toResponse(
          new CedarDependencyUnavailableException("MongoDB is unavailable", exception));
    } else if (isSqlUnavailable(exception)) {
      return new CedarCedarExceptionMapper().toResponse(
          new CedarDependencyUnavailableException("SQL database is unavailable", exception));
    } else if (isRedisUnavailable(exception)) {
      return new CedarCedarExceptionMapper().toResponse(
          new CedarDependencyUnavailableException("Redis is unavailable", exception));
    }

    Response clientResponse = clientResponse(exception);
    if (clientResponse != null) {
      logMappedException(log, ":CEM:", exception, clientResponse.getStatus(), false);
      return clientResponse;
    }

    String errorId = UUID.randomUUID().toString();
    logMappedException(log, ":CEM:", exception,
        CedarResponseStatus.INTERNAL_SERVER_ERROR.getStatusCode(), true, errorId);

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
        .entity(CedarError.from(errorPack, errorId))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  private Response clientResponse(Exception exception) {
    if (!(exception instanceof WebApplicationException webException)) return null;
    Response original = webException.getResponse();
    // Jersey normally bypasses mappers for an explicit entity. Preserve that contract for callers
    // that invoke this mapper directly too (operation reports and deliberate upstream payloads).
    if (original.hasEntity()) return original;
    // Jersey treats query conversion as a URI lookup failure (404). A malformed query is a
    // client input error; keep path conversion's 404, but classify query input as 400.
    int status = exception instanceof org.glassfish.jersey.server.ParamException.QueryParamException
        ? 400 : original.getStatus();
    Response normalized = CedarResponse.status(status).build();
    Response.ResponseBuilder result = Response.fromResponse(normalized);
    // Preserve protocol metadata, including multi-valued challenges. Representation metadata belongs
    // to the newly generated JSON body, not to the bodyless response carried by the exception.
    original.getHeaders().forEach((name, values) -> {
      if (!java.util.Set.of("content-type", "content-length", "content-encoding", "transfer-encoding")
          .contains(name.toLowerCase(java.util.Locale.ROOT))) {
        result.header(name, null);
        values.forEach(value -> result.header(name, value));
      }
    });
    return result.build();
  }

}
