package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.util.http.CedarResponse;

/** Replaces Dropwizard's private JSON-error body with the common CEDAR envelope. */
@Provider
@Priority(Priorities.USER - 100)
public class CedarJsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

  @Override
  public Response toResponse(JsonProcessingException exception) {
    if (exception instanceof JsonGenerationException || exception instanceof InvalidDefinitionException) {
      return CedarResponse.internalServerError().exception(exception).build();
    }
    return CedarResponse.badRequest()
        .errorKey(CedarErrorKey.MALFORMED_JSON_REQUEST_BODY)
        .message("Unable to process JSON")
        .build();
  }
}
