package org.metadatacenter.cedar.util.dw;

import io.dropwizard.jersey.optional.EmptyOptionalException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.metadatacenter.util.http.CedarResponse;

/** Gives an empty optional the same 404 status as Dropwizard, now with the common body. */
@Provider
@Priority(Priorities.USER - 100)
public class CedarEmptyOptionalExceptionMapper implements ExceptionMapper<EmptyOptionalException> {

  @Override
  public Response toResponse(EmptyOptionalException exception) {
    return CedarResponse.notFound().build();
  }
}
