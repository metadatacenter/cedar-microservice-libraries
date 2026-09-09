package org.metadatacenter.rest.context;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body the endpoint cannot read as the type it expects is the caller's mistake. Both request-body
 * implementations used to throw an unstatused assertion for it, which the mapper rendered as a 500
 * with no error key, so a client sending an unknown key to PUT /groups/{id}/users could not tell its
 * typo from an outage.
 */
class RequestBodyConversionTest {

  public static class GroupUsers {
    public String groupId;
  }

  @Test
  @DisplayName("A JSON body of the wrong shape is a 400 that names the type")
  void wrongShapeIsABadRequest() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree("{\"groupId\": \"g1\", \"unexpected\": true}");
    CedarException e = assertThrows(CedarAssertionException.class,
        () -> new HttpRequestJsonBody(body).convert(GroupUsers.class));
    assertEquals(CedarResponseStatus.BAD_REQUEST, e.getErrorPack().getStatus());
    assertEquals(CedarErrorKey.INVALID_INPUT, e.getErrorPack().getErrorKey());
    assertEquals("GroupUsers", e.getErrorPack().getParameters().get("type"));
    assertTrue(e.getErrorPack().getMessage().contains("GroupUsers"));
  }

  @Test
  @DisplayName("A body of the right shape converts")
  void rightShapeConverts() throws Exception {
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree("{\"groupId\": \"g1\"}");
    assertEquals("g1", new HttpRequestJsonBody(body).convert(GroupUsers.class).groupId);
  }

  @Test
  @DisplayName("An absent body is a 400 that says the data is missing")
  void emptyBodyIsABadRequest() {
    CedarException e = assertThrows(CedarAssertionException.class,
        () -> new HttpRequestEmptyBody().convert(GroupUsers.class));
    assertEquals(CedarResponseStatus.BAD_REQUEST, e.getErrorPack().getStatus());
    assertEquals(CedarErrorKey.MISSING_DATA, e.getErrorPack().getErrorKey());
    assertEquals("GroupUsers", e.getErrorPack().getParameters().get("type"));
  }
}
