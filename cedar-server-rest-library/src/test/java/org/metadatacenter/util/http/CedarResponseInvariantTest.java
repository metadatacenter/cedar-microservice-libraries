package org.metadatacenter.util.http;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.server.result.BackendCallResult;
import static org.junit.jupiter.api.Assertions.*;

class CedarResponseInvariantTest {
  @Test void successesDoNotInventAnErrorBody() {
    assertFalse(CedarResponse.ok().build().hasEntity());
    assertFalse(CedarResponse.created(URI.create("/item")).build().hasEntity());
  }
  @Test void noContentDiscardsEvenAnExplicitEntity() {
    assertFalse(CedarResponse.noContent().entity("payload").build().hasEntity());
  }
  @Test void theLastStatusWinsOverCreated() {
    var response = CedarResponse.created(URI.create("/item")).status(CedarResponseStatus.CONFLICT).build();
    assertEquals(409,response.getStatus());
    assertEquals(409,((CedarError)response.getEntity()).statusCode);
    assertNull(response.getLocation());
  }
  @Test void createdSelectsStatusAtTheCallSite() {
    var response = CedarResponse.conflict().created(URI.create("/item")).build();
    assertEquals(201,response.getStatus());
    assertFalse(response.hasEntity());
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {400,401,403,404,405,409,412,422,428,429,499,500,502,503,504,599})
  void everyNumericErrorKeepsItsStatusAndDiagnostics(int status) {
    var response = CedarResponse.status(status).message("reason").parameter("upstreamStatusCode",status).build();
    assertEquals(status,response.getStatus());
    CedarError error = (CedarError)response.getEntity();
    assertEquals(status,error.statusCode);
    assertEquals("reason",error.message);
    assertEquals(status,error.parameters.get("upstreamStatusCode"));
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {100,101,199,204,205,304})
  void numericBodylessStatusesDiscardEntities(int status) {
    assertFalse(CedarResponse.status(status).entity("payload").build().hasEntity());
  }
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {0,99,600})
  void invalidNumericStatusesAreRejected(int status) {
    assertThrows(IllegalArgumentException.class, () -> CedarResponse.status(status));
  }
  @Test void invalidInputsAreRejectedIntentionally() {
    assertThrows(IllegalArgumentException.class, () -> CedarResponse.status((CedarResponseStatus)null));
    assertThrows(IllegalArgumentException.class, () -> CedarResponse.from(null));
    assertThrows(IllegalArgumentException.class, () -> CedarResponse.from(new BackendCallResult<>()));
  }
}
