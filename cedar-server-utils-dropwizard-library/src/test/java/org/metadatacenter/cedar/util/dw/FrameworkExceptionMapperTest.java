package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.core.JsonParseException;
import io.dropwizard.jersey.optional.EmptyOptionalException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import org.eclipse.jetty.io.EofException;
import org.glassfish.jersey.server.internal.LocalizationMessages;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.http.CedarError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FrameworkExceptionMapperTest {

  @Test
  void malformedJsonUsesTheCommonBadRequestEnvelope() {
    try (Response response = new CedarJsonProcessingExceptionMapper()
        .toResponse(new JsonParseException("bad JSON"))) {
      CedarError error = canonical(response, 400);
      assertEquals("malformedJsonRequestBody", error.errorKey);
      assertEquals("Unable to process JSON", error.message);
    }
  }

  @Test
  void frameworkBodylessOutcomesGainTheCommonEnvelope() {
    List<Response> responses = List.of(
        new CedarEmptyOptionalExceptionMapper().toResponse(EmptyOptionalException.INSTANCE),
        new CedarEarlyEofExceptionMapper().toResponse(new EofException("disconnected")),
        new CedarIllegalStateExceptionMapper().toResponse(
            new IllegalStateException(LocalizationMessages.FORM_PARAM_CONTENT_TYPE_ERROR())));
    try {
      canonical(responses.get(0), 404);
      canonical(responses.get(1), 400);
      canonical(responses.get(2), 415);
    } finally {
      responses.forEach(Response::close);
    }
  }

  @Test
  void unexpectedIllegalStateHasAClientSafeCorrelationId() {
    try (Response response = new CedarIllegalStateExceptionMapper()
        .toResponse(new IllegalStateException("internal detail"))) {
      assertNotNull(canonical(response, 500).errorId);
    }
  }

  @Test
  void cedarMappersOutrankDropwizardPeersForTheSameExceptionTypes() {
    List<Class<?>> mappers = List.of(
        CedarJsonProcessingExceptionMapper.class,
        CedarJerseyViolationExceptionMapper.class,
        CedarEmptyOptionalExceptionMapper.class,
        CedarIllegalStateExceptionMapper.class,
        CedarEarlyEofExceptionMapper.class);
    for (Class<?> mapper : mappers) {
      assertEquals(Priorities.USER - 100, mapper.getAnnotation(Priority.class).value(), mapper.getSimpleName());
    }
  }

  private static CedarError canonical(Response response, int statusCode) {
    assertEquals(statusCode, response.getStatus());
    CedarError error = assertInstanceOf(CedarError.class, response.getEntity());
    assertEquals(statusCode, error.statusCode);
    return error;
  }
}
