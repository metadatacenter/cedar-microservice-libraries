package org.metadatacenter.rest.context;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A command or options body accepts the properties its endpoint declares and no others. A handler
 * that reads the properties it knows out of the tree cannot tell a misspelled one from an absent
 * one, so a caller who wrote "schema:naem" was answered with a success that changed nothing.
 */
class RequestBodyClosedContractTest {

  @Test
  @DisplayName("A body carrying only accepted properties passes")
  void acceptedPropertiesPass() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree("{\"schema:name\": \"Editors\"}");
    assertDoesNotThrow(() -> new HttpRequestJsonBody(body).mustHaveOnly("schema:name", "schema:description"));
  }

  @Test
  @DisplayName("An unsupported property is a 400 that names it and what the endpoint accepts")
  void unsupportedPropertyIsABadRequest() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree("{\"schema:naem\": \"Editors\"}");
    CedarException e = assertThrows(CedarAssertionException.class,
        () -> new HttpRequestJsonBody(body).mustHaveOnly("schema:name", "schema:description"));
    assertEquals(CedarResponseStatus.BAD_REQUEST, e.getErrorPack().getStatus());
    assertEquals(CedarErrorKey.INVALID_INPUT, e.getErrorPack().getErrorKey());
    assertEquals(List.of("schema:naem"), e.getErrorPack().getParameters().get("unsupportedProperties"));
    assertEquals(List.of("schema:name", "schema:description"),
        e.getErrorPack().getParameters().get("acceptedProperties"));
    assertTrue(e.getErrorPack().getMessage().contains("schema:naem"));
  }

  @Test
  @DisplayName("Every unsupported property is reported, not only the first")
  void allUnsupportedPropertiesAreReported() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree(
        "{\"schema:name\": \"Editors\", \"@id\": \"https://example.org/g1\", \"specialGroup\": \"x\"}");
    CedarException e = assertThrows(CedarAssertionException.class,
        () -> new HttpRequestJsonBody(body).mustHaveOnly("schema:name", "schema:description"));
    assertEquals(List.of("@id", "specialGroup"), e.getErrorPack().getParameters().get("unsupportedProperties"));
  }

  @Test
  @DisplayName("A property whose value is null still has to be accepted")
  void anExplicitNullIsCheckedLikeAnyProperty() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree("{\"schema:naem\": null}");
    assertThrows(CedarAssertionException.class,
        () -> new HttpRequestJsonBody(body).mustHaveOnly("schema:name"));
  }

  @Test
  @DisplayName("A body that is not a JSON object carries no properties to check")
  void aNonObjectBodyIsLeftToTheEndpoint() throws Exception {
    JsonNode array = JsonMapper.STRICT_MAPPER.readTree("[{\"schema:naem\": \"Editors\"}]");
    assertDoesNotThrow(() -> new HttpRequestJsonBody(array).mustHaveOnly("schema:name"));
    assertDoesNotThrow(() -> new HttpRequestJsonBody(null).mustHaveOnly("schema:name"));
  }

  @Test
  @DisplayName("An absent body carries no properties to check")
  void anAbsentBodyPasses() {
    assertDoesNotThrow(() -> new HttpRequestEmptyBody().mustHaveOnly("schema:name"));
  }
}
