package org.metadatacenter.cedar.util.dw;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.jetty.io.EofException;
import org.metadatacenter.util.http.CedarResponse;

/** Retains Dropwizard's 400 classification for a client disconnect and uses the common body. */
@Provider
@Priority(Priorities.USER - 100)
public class CedarEarlyEofExceptionMapper implements ExceptionMapper<EofException> {

  @Override
  public Response toResponse(EofException exception) {
    return CedarResponse.badRequest().message("The client disconnected before the request completed").build();
  }
}
