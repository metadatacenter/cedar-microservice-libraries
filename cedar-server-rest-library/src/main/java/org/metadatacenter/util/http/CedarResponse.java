package org.metadatacenter.util.http;

import com.google.common.collect.Maps;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.error.CedarErrorReasonKey;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.operation.CedarOperationDescriptor;
import org.metadatacenter.server.result.BackendCallError;
import org.metadatacenter.server.result.BackendCallResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public abstract class CedarResponse {

  private static final Logger log = LoggerFactory.getLogger(CedarResponse.class);

  private static CedarResponseBuilder newResponseBuilder() {
    return new CedarResponseBuilder();
  }

  private static CedarResponseBuilder newResponseBuilder(BackendCallResult backendCallResult) {
    if (backendCallResult != null) {
      BackendCallError firstError = backendCallResult.getFirstError();
      if (firstError != null) {
        CedarErrorPack errorPack = firstError.getErrorPack();
        if (errorPack != null) {
          return new CedarResponseBuilder(errorPack);
        }
      }
    }
    throw new IllegalArgumentException("An unsuccessful backend result with an error pack is required");
  }

  public static class CedarResponseBuilder {

    private final CedarErrorPack errorPack;
    private int statusCode = 500;
    private Exception exception;
    private Object entity;
    private URI createdResourceUri;
    private String type;
    private String fileName;
    private Map<String, Object> headers = Maps.newHashMap();

    protected CedarResponseBuilder() {
      this.errorPack = new CedarErrorPack();
    }

    public CedarResponseBuilder(CedarErrorPack errorPack) {
      this.errorPack = new CedarErrorPack(errorPack);
      statusCode = this.errorPack.getStatusCode();
      exception = this.errorPack.getOriginalException();
    }

    /**
     * Whether the status this response carries may not have a body.
     *
     * <p>RFC 9110 forbids one on 204, 205 and 304, and on every 1xx. A body sent with those is at best
     * ignored and at worst confuses an intermediary about where the next message starts.
     */
    private boolean statusForbidsABody() {
      int code = statusCode;
      return code == CedarResponseStatus.NO_CONTENT.getStatusCode() || code == 205 || code == 304 || (code >= 100 && code < 200);
    }

    public Response build() {
      Response.ResponseBuilder responseBuilder = Response.noContent();
      boolean generatedErrorEntity = false;
      responseBuilder.status(statusCode);

      if (!headers.isEmpty()) {
        for (String property : headers.keySet()) {
          responseBuilder.header(property, headers.get(property));
        }
      }
      if (statusCode == 401 && headers.keySet().stream().noneMatch(HttpHeaders.WWW_AUTHENTICATE::equalsIgnoreCase)) {
        responseBuilder.header(HttpHeaders.WWW_AUTHENTICATE, HttpConstants.HTTP_AUTH_CHALLENGE);
      }
      responseBuilder.header(HttpConstants.HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
          CustomHttpConstants.EXPOSED_HEADERS_VALUE);
      if (createdResourceUri != null && statusCode == 201) {
        responseBuilder.location(createdResourceUri);
      }
      if (statusForbidsABody()) {
        return responseBuilder.build();
      }
      if (entity != null) {
        responseBuilder.entity(entity);
      } else {
        String errorId = null;
        if (exception != null) {
          // Never serialize the stack trace to the client: it leaks class names, source files, line
          // numbers and internal architecture (including on unauthenticated routes). Log the exception
          // server-side under a correlation id and return only that id, so an operator can find the
          // full detail in the logs while the client gets nothing exploitable.
          errorId = UUID.randomUUID().toString();
          log.error("Error response {} (status {}): {}", errorId, statusCode, exception.getMessage(),
              exception);
        }

        // An empty success is not an error. Only errors gain a generated envelope.
        if (statusCode >= 400) {
          CedarError error = CedarError.from(errorPack, errorId, statusCode);
          responseBuilder.entity(error);
          generatedErrorEntity = true;
        }
      }
      if (type != null) {
        responseBuilder.type(type);
      } else if (generatedErrorEntity) {
        responseBuilder.type(MediaType.APPLICATION_JSON_TYPE);
      }
      if (fileName != null) {
        responseBuilder.header(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
      }
      return responseBuilder.build();
    }

    public CedarResponseBuilder status(CedarResponseStatus status) {
      if (status == null) throw new IllegalArgumentException("HTTP status must not be null");
      return status(status.getStatusCode());
    }

    /** Numeric statuses preserve upstream answers outside the named CEDAR subset. */
    public CedarResponseBuilder status(int status) {
      if (status < 100 || status > 599) throw new IllegalArgumentException("HTTP status must be between 100 and 599");
      this.statusCode = status;
      return this;
    }

    public CedarResponseBuilder entity(Object entity) {
      this.entity = entity;
      return this;
    }

    public CedarResponseBuilder id(Object id) {
      return this.parameter("id", id);
    }

    public CedarResponseBuilder parameter(String key, Object value) {
      this.errorPack.parameter(key, value);
      return this;
    }

    public CedarResponseBuilder object(String key, Object value) {
      this.errorPack.object(key, value);
      return this;
    }

    public CedarResponseBuilder errorKey(CedarErrorKey errorKey) {
      this.errorPack.errorKey(errorKey);
      return this;
    }

    public CedarResponseBuilder errorReasonKey(CedarErrorReasonKey errorReasonKey) {
      this.errorPack.errorReasonKey(errorReasonKey);
      return this;
    }

    public CedarResponseBuilder message(String message) {
      this.errorPack.message(message);
      return this;
    }

    public CedarResponseBuilder exception(Exception exception) {
      this.exception = exception;
      return this;
    }

    public CedarResponseBuilder created(URI createdResourceUri) {
      this.createdResourceUri = Objects.requireNonNull(createdResourceUri, "Created resource location");
      return status(CedarResponseStatus.CREATED);
    }

    public CedarResponseBuilder header(String property, Object value) {
      headers.put(property, value);
      return this;
    }

    public CedarResponseBuilder operation(CedarOperationDescriptor operation) {
      this.errorPack.operation(operation);
      return this;
    }

    public CedarResponseBuilder type(String type) {
      this.type = type;
      return this;
    }

    public CedarResponseBuilder contentDispositionAttachment(String fileName) {
      this.fileName = fileName;
      return this;
    }
  }

  public static CedarResponseBuilder ok() {
    return newResponseBuilder().status(CedarResponseStatus.OK);
  }

  public static CedarResponseBuilder internalServerError() {
    return newResponseBuilder().status(CedarResponseStatus.INTERNAL_SERVER_ERROR);
  }

  public static CedarResponseBuilder badGateway() {
    return newResponseBuilder().status(CedarResponseStatus.BAD_GATEWAY);
  }

  public static CedarResponseBuilder noContent() {
    return newResponseBuilder().status(CedarResponseStatus.NO_CONTENT);
  }

  public static CedarResponseBuilder notFound() {
    return newResponseBuilder().status(CedarResponseStatus.NOT_FOUND);
  }

  public static CedarResponseBuilder unauthorized() {
    return newResponseBuilder().status(CedarResponseStatus.UNAUTHORIZED);
  }

  public static CedarResponseBuilder forbidden() {
    return newResponseBuilder().status(CedarResponseStatus.FORBIDDEN);
  }

  public static CedarResponseBuilder badRequest() {
    return newResponseBuilder().status(CedarResponseStatus.BAD_REQUEST);
  }

  public static CedarResponseBuilder notAcceptable() {
    return newResponseBuilder().status(CedarResponseStatus.NOT_ACCEPTABLE);
  }

  public static CedarResponseBuilder methodNotAllowed() {
    return newResponseBuilder().status(CedarResponseStatus.METHOD_NOT_ALLOWED);
  }

  /** The answer to a conditional write sent without If-Match; see {@link RevisionPreconditionParser#isAbsent}. */
  public static CedarResponseBuilder preconditionRequired() {
    return newResponseBuilder().status(CedarResponseStatus.PRECONDITION_REQUIRED);
  }

  public static CedarResponseBuilder conflict() {
    return newResponseBuilder().status(CedarResponseStatus.CONFLICT);
  }

  public static CedarResponseBuilder unsupportedMediaType() {
    return newResponseBuilder().status(CedarResponseStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  public static CedarResponseBuilder httpVersionNotSupported() {
    return newResponseBuilder().status(CedarResponseStatus.HTTP_VERSION_NOT_SUPPORTED);
  }

  public static CedarResponseBuilder created(URI createdResourceLocation) {
    return newResponseBuilder().status(CedarResponseStatus.CREATED).created(createdResourceLocation);
  }

  public static CedarResponseBuilder status(CedarResponseStatus status) {
    return newResponseBuilder().status(status);
  }

  public static CedarResponseBuilder status(int status) {
    return newResponseBuilder().status(status);
  }

  /** Render a failed backend call; successful payloads are the resource's responsibility. */
  public static Response from(BackendCallResult backendCallResult) {
    return newResponseBuilder(backendCallResult).build();
  }

}
