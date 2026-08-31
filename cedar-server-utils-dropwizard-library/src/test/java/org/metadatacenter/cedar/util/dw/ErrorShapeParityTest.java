package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.util.http.CedarResponse;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two error shapes agree on the keys a client reads.
 *
 * <p>CEDAR renders a failure two ways: the map {@link CedarResponse} builds, and the
 * {@link CedarErrorPack} the exception mapper serializes. They named the same things differently, so a
 * client reading {@code errorMessage} got null from every exception-mapped failure and one reading
 * {@code statusCode} got nothing from the pack at all. Both now carry both.
 *
 * <p>This asserts the keys, not the structure. Merging the two into one class is a separate change:
 * {@code build()} reduces an exception to a correlation id rather than serializing it, and a pack has no
 * such step, so emitting one from the builder would newly expose what the map deliberately withholds.
 */
class ErrorShapeParityTest {

  private static final Set<String> SHARED_KEYS =
      Set.of("errorMessage", "message", "status", "statusCode", "errorKey", "errorReasonKey");

  @SuppressWarnings("unchecked")
  private static Map<String, Object> builderShape() {
    Response response = CedarResponse.notFound().errorMessage("the artifact was not found").build();
    return (Map<String, Object>) response.getEntity();
  }

  private static Map<String, Object> packShape() {
    CedarErrorPack pack = new CedarErrorPack()
        .status(CedarResponseStatus.NOT_FOUND)
        .message("the artifact was not found");
    return new ObjectMapper().convertValue(pack, Map.class);
  }

  @Test
  @DisplayName("Both shapes carry every shared key")
  void bothShapesCarryTheSharedKeys() {
    for (String key : SHARED_KEYS) {
      assertTrue(builderShape().containsKey(key), "the builder's map is missing " + key);
      assertTrue(packShape().containsKey(key), "the error pack is missing " + key);
    }
  }

  @Test
  @DisplayName("The message reads the same under either key, in either shape")
  void theMessageAgreesAcrossKeysAndShapes() {
    assertEquals("the artifact was not found", builderShape().get("errorMessage"));
    assertEquals("the artifact was not found", builderShape().get("message"));
    assertEquals("the artifact was not found", packShape().get("errorMessage"));
    assertEquals("the artifact was not found", packShape().get("message"));
  }

  @Test
  @DisplayName("The numeric status agrees across shapes")
  void theStatusCodeAgrees() {
    assertEquals(404, ((Number) builderShape().get("statusCode")).intValue());
    assertEquals(404, ((Number) packShape().get("statusCode")).intValue());
  }
}
