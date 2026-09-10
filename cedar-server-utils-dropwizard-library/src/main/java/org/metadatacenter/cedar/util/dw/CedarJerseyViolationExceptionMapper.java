package org.metadatacenter.cedar.util.dw;

import io.dropwizard.jersey.validation.ConstraintMessage;
import io.dropwizard.jersey.validation.JerseyViolationException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.model.Invocable;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.util.http.CedarResponse;

import java.util.List;

/** Reports Dropwizard's validation details through the common envelope. */
@Provider
@Priority(Priorities.USER - 100)
public class CedarJerseyViolationExceptionMapper implements ExceptionMapper<JerseyViolationException> {

  @Override
  public Response toResponse(JerseyViolationException exception) {
    Invocable invocable = exception.getInvocable();
    List<String> errors = exception.getConstraintViolations().stream()
        .map(violation -> ConstraintMessage.getMessage(violation, invocable))
        .toList();
    CedarResponseStatus status = CedarResponseStatus.fromStatusCode(
        ConstraintMessage.determineStatus(exception.getConstraintViolations(), invocable));

    return CedarResponse.status(status)
        .errorKey(CedarErrorKey.INVALID_INPUT)
        .message("Request validation failed")
        .object("validationErrors", errors)
        .build();
  }
}
