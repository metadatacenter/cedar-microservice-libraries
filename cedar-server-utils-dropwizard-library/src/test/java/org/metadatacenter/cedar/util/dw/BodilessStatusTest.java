package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.http.CedarResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A status that may not carry a body does not get one.
 *
 * <p>The builder assembles a diagnostic map for every response with no explicit entity, which is what an
 * error body is made of. RFC 9110 forbids a body on 204, so the two call sites that answer no-content
 * were sending one.
 */
class BodilessStatusTest {

  @Test
  @DisplayName("A 204 carries no entity")
  void noContentCarriesNoEntity() {
    Response response = CedarResponse.noContent().build();
    assertEquals(204, response.getStatus());
    assertNull(response.getEntity(), "RFC 9110 forbids a body on 204");
    assertFalse(response.hasEntity());
  }

  @Test
  @DisplayName("An error status still carries its diagnostic body")
  void errorStatusesKeepTheirBody() {
    assertNotNull(CedarResponse.badRequest().build().getEntity(),
        "the diagnostic map is what an error body is made of");
    assertNotNull(CedarResponse.notFound().build().getEntity());
    assertNotNull(CedarResponse.unauthorized().build().getEntity());
  }

  @Test
  @DisplayName("An explicit entity is returned whatever the status")
  void anExplicitEntityIsAlwaysHonoured() {
    Response response = CedarResponse.ok().entity("payload").build();
    assertEquals("payload", response.getEntity());
  }
}
