package org.metadatacenter.cedar.util.dw;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.metadatacenter.util.http.CedarResponse;

/** Retains Dropwizard's form-content classification without emitting its private error type. */
@Provider
@Priority(Priorities.USER - 100)
public class CedarIllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

  @Override
  public Response toResponse(IllegalStateException exception) {
    if (LocalizationMessages.FORM_PARAM_CONTENT_TYPE_ERROR().equals(exception.getMessage())) {
      return CedarResponse.unsupportedMediaType().build();
    }
    return CedarResponse.internalServerError().exception(exception).build();
  }
}
