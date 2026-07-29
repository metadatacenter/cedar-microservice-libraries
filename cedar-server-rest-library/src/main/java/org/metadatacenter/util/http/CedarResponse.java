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

import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
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
    return null;
  }

  public static class CedarResponseBuilder {

    private final Map<String, Object> parameters;
    private final Map<String, Object> objects;
    private CedarErrorKey errorKey;
    private CedarErrorReasonKey errorReasonKey;
    private String errorMessage;
    private Exception exception;
    private CedarResponseStatus status;
    private Object entity;
    private URI createdResourceUri;
    private CedarOperationDescriptor operation;
    private String type;
    private String fileName;
    private Map<String, Object> headers = Maps.newHashMap();

    protected CedarResponseBuilder() {
      this.parameters = new HashMap<>();
      this.objects = new HashMap<>();
    }

    public CedarResponseBuilder(CedarErrorPack errorPack) {
      parameters = errorPack.getParameters();
      objects = errorPack.getObjects();
      errorKey = errorPack.getErrorKey();
      errorReasonKey = errorPack.getErrorReasonKey();
      errorMessage = errorPack.getMessage();
      exception = errorPack.getOriginalException();
      status = errorPack.getStatus();
      operation = errorPack.getOperation();
    }

    public Response build() {
      Response.ResponseBuilder responseBuilder = Response.noContent();
      responseBuilder.status(status.getStatusCode());

      if (!headers.isEmpty()) {
        for (String property : headers.keySet()) {
          responseBuilder.header(property, headers.get(property));
        }
      }
      responseBuilder.header(HttpConstants.HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS,
          CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS + "," + HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION);
      if (createdResourceUri != null) {
        responseBuilder.status(CedarResponseStatus.CREATED.getStatusCode()).location(createdResourceUri);
      }
      if (entity != null) {
        responseBuilder.entity(entity);
      } else {
        Map<String, Object> r = new HashMap<>();
        r.put("parameters", parameters);
        r.put("objects", objects);
        r.put("errorKey", errorKey);
        r.put("errorReasonKey", errorReasonKey);
        r.put("errorMessage", errorMessage);
        r.put("status", status);
        r.put("statusCode", status.getStatusCode());
        r.put("operation", operation == null ? null : operation.asJson());
        if (exception != null) {
          // Never serialize the stack trace to the client: it leaks class names, source files, line
          // numbers and internal architecture (including on unauthenticated routes). Log the exception
          // server-side under a correlation id and return only that id, so an operator can find the
          // full detail in the logs while the client gets nothing exploitable.
          String errorId = UUID.randomUUID().toString();
          log.error("Error response {} (status {}): {}", errorId, status, exception.getMessage(), exception);
          r.put("errorId", errorId);
        }

        if (!r.isEmpty()) {
          responseBuilder.entity(r);
        }
      }
      if (type != null) {
        responseBuilder.type(type);
      }
      if (fileName != null) {
        responseBuilder.header(HttpConstants.HTTP_HEADER_CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
      }
      return responseBuilder.build();
    }

    public CedarResponseBuilder status(CedarResponseStatus status) {
      this.status = status;
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
      this.parameters.put(key, value);
      return this;
    }

    public CedarResponseBuilder object(String key, Object value) {
      this.objects.put(key, value);
      return this;
    }

    public CedarResponseBuilder errorKey(CedarErrorKey errorKey) {
      this.errorKey = errorKey;
      return this;
    }

    public CedarResponseBuilder errorReasonKey(CedarErrorReasonKey errorReasonKey) {
      this.errorReasonKey = errorReasonKey;
      return this;
    }

    public CedarResponseBuilder errorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public CedarResponseBuilder exception(Exception exception) {
      this.exception = exception;
      return this;
    }

    public CedarResponseBuilder created(URI createdResourceUri) {
      this.createdResourceUri = createdResourceUri;
      return this;
    }

    public CedarResponseBuilder header(String property, Object value) {
      headers.put(property, value);
      return this;
    }

    public CedarResponseBuilder operation(CedarOperationDescriptor operation) {
      this.operation = operation;
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

  public static Response from(BackendCallResult backendCallResult) {
    return newResponseBuilder(backendCallResult).build();
  }

}
