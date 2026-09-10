package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.util.http.CedarError;
import org.metadatacenter.util.http.CedarResponse;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Both error producers emit the same runtime envelope, and it carries only its declared fields.
 *
 * <p>{@link CedarErrorPack} remains the internal accumulator carried by exceptions. It is converted
 * at the HTTP boundary, so neither it nor its exception fields can become a second public shape.
 */
class ErrorShapeParityTest {

  private static final Set<String> ENVELOPE_KEYS = Set.of(
      "status", "statusCode", "errorKey", "errorReasonKey", "errorType", "message",
      "parameters", "objects", "entities", "suggestedAction", "operation", "errorId");

  @SuppressWarnings("unchecked")
  private static CedarError builderEntity() {
    Response response = CedarResponse.notFound().message("the artifact was not found").build();
    return (CedarError) response.getEntity();
  }

  private static CedarError mapperEntity() {
    CedarProcessingException exception = new CedarProcessingException("the artifact was not found");
    exception.getErrorPack().status(CedarResponseStatus.NOT_FOUND);
    return (CedarError) new CedarCedarExceptionMapper().toResponse(exception).getEntity();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> shape(CedarError error) {
    return new ObjectMapper().convertValue(error, Map.class);
  }

  @Test
  @DisplayName("Both producers return CedarError with the complete envelope")
  void bothProducersReturnTheCanonicalEnvelope() {
    assertEquals(ENVELOPE_KEYS, shape(builderEntity()).keySet());
    assertEquals(ENVELOPE_KEYS, shape(mapperEntity()).keySet());
  }

  @Test
  @DisplayName("The message reads the same in either shape")
  void theMessageAgreesAcrossShapes() {
    assertEquals("the artifact was not found", builderEntity().message);
    assertEquals("the artifact was not found", mapperEntity().message);
  }

  @Test
  @DisplayName("The envelope carries no field outside its declared set")
  void theEnvelopeCarriesNothingUndeclared() {
    Response response = CedarResponse.badRequest().message("the request was malformed").build();

    assertEquals(ENVELOPE_KEYS, shape((CedarError) response.getEntity()).keySet());
  }

  @Test
  @DisplayName("The numeric status agrees across shapes")
  void theStatusCodeAgrees() {
    assertEquals(404, builderEntity().statusCode);
    assertEquals(404, mapperEntity().statusCode);
  }

  @Test
  @DisplayName("Generated error envelopes declare JSON")
  void generatedErrorEnvelopesDeclareJson() {
    Response response = CedarResponse.badRequest().build();

    assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
  }

  @Test
  @DisplayName("Internal exception objects never enter the public envelope")
  void internalExceptionsAreNotSerialized() {
    Map<String, Object> shape = shape(mapperEntity());
    assertTrue(!shape.containsKey("originalException"));
    assertTrue(!shape.containsKey("sourceException"));
  }
}
