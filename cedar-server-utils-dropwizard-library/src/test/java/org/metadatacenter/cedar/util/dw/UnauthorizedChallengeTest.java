package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.util.http.CedarResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every 401 names the schemes a client may use.
 *
 * <p>CEDAR accepts {@code Bearer} and {@code apiKey}, and a client that receives neither in a challenge
 * has to guess. The two are asserted together because they are built in different places: most refusals
 * go through {@link CedarResponse}, while an unauthenticated request throws from
 * {@code buildRequestContext} and is rendered by {@link CedarCedarExceptionMapper}, which builds its own
 * response.
 */
class UnauthorizedChallengeTest {

  @Test
  @DisplayName("The challenge names both schemes CEDAR accepts")
  void challengeNamesBothSchemes() {
    assertTrue(HttpConstants.HTTP_AUTH_CHALLENGE.contains(HttpConstants.HTTP_AUTH_HEADER_BEARER_PREFIX.trim()),
        "the challenge should name the Bearer scheme: " + HttpConstants.HTTP_AUTH_CHALLENGE);
    assertTrue(HttpConstants.HTTP_AUTH_CHALLENGE.contains(HttpConstants.HTTP_AUTH_HEADER_APIKEY_PREFIX.trim()),
        "the challenge should name the apiKey scheme: " + HttpConstants.HTTP_AUTH_CHALLENGE);
  }

  @Test
  @DisplayName("A 401 built through CedarResponse carries the challenge")
  void cedarResponseUnauthorizedCarriesTheChallenge() {
    Response response = CedarResponse.unauthorized().build();
    assertEquals(401, response.getStatus());
    assertEquals(HttpConstants.HTTP_AUTH_CHALLENGE, response.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
  }

  @Test
  @DisplayName("A response that is not a 401 carries no challenge")
  void otherStatusesCarryNoChallenge() {
    assertNull(CedarResponse.forbidden().build().getHeaderString(HttpHeaders.WWW_AUTHENTICATE),
        "403 means the credential was understood and refused, so a challenge would invite a retry that cannot help");
    assertNull(CedarResponse.badRequest().build().getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
    assertNull(CedarResponse.notFound().build().getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
  }
}
