package org.metadatacenter.server.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.AuthRequest;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How the Authorization header of every request to every server becomes a credential: a bearer token,
 * an API key, a scheme no server accepts, or none. It was reached only through the Keycloak library's
 * resolver test and the server suites.
 */
class CedarAuthFromRequestFactoryTest {

  @Test
  void aBearerHeaderCarriesItsTokenInAnyCase() {
    for (String scheme : new String[]{"Bearer ", "bearer ", "BEARER "}) {
      AuthRequest auth = CedarAuthFromRequestFactory.fromRequest(withAuthorization(scheme + "token-1"));
      assertInstanceOf(CedarBearerAuthRequest.class, auth, scheme);
      assertEquals("token-1", auth.getAuthString(), scheme);
      assertEquals(scheme + "token-1", auth.getAuthHeader());
    }
  }

  @Test
  void anApiKeyHeaderCarriesItsKeyInAnyCase() {
    for (String scheme : new String[]{"apiKey ", "apikey ", "APIKEY "}) {
      AuthRequest auth = CedarAuthFromRequestFactory.fromRequest(withAuthorization(scheme + "key-1"));
      assertInstanceOf(CedarApiKeyAuthRequest.class, auth, scheme);
      assertEquals("key-1", auth.getAuthString(), scheme);
    }
  }

  /** A scheme no server accepts is kept as it was sent and carries no credential. */
  @Test
  void anotherSchemeCarriesNoCredential() {
    for (String header : new String[]{"Basic dXNlcjpwYXNz", "apiKeykey-1", "Bearertoken-1", ""}) {
      AuthRequest auth = CedarAuthFromRequestFactory.fromRequest(withAuthorization(header));
      assertInstanceOf(CedarUnknownAuthRequest.class, auth, header);
      assertNull(auth.getAuthString(), header);
      assertEquals(header, auth.getAuthHeader());
    }
  }

  @Test
  void noHeaderOrNoRequestCarriesNothing() {
    assertInstanceOf(CedarNoAuthRequest.class, CedarAuthFromRequestFactory.fromRequest(withAuthorization(null)));
    assertInstanceOf(CedarNoAuthRequest.class, CedarAuthFromRequestFactory.fromRequest(null));
  }

  private static HttpServletRequest withAuthorization(String value) {
    return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
        new Class<?>[]{HttpServletRequest.class},
        (proxy, method, args) -> method.getName().equals("getHeader") && "Authorization".equals(args[0]) ? value : null);
  }
}
