package org.metadatacenter.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.common.VerificationException;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;
import org.metadatacenter.config.KeycloakConfig;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class KeycloakTokenVerifierTest {
  private HttpServer server;
  private KeycloakTokenVerifier verifier;
  private String issuer;
  private String discovery;
  private String keys;
  private int status;
  private KeyPair oldKey;
  private KeyPair newKey;
  private final AtomicLong clock = new AtomicLong();
  private final AtomicInteger requests = new AtomicInteger();

  @BeforeEach
  void start() throws Exception {
    oldKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    newKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    issuer = base + "/realms/CEDAR";
    discovery = JsonSerialization.writeValueAsString(Map.of("issuer", issuer, "jwks_uri", issuer + "/certs"));
    publish(jwk("old", oldKey));
    status = 200;
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      String source = exchange.getRequestURI().getPath().endsWith("/certs") ? keys : discovery;
      byte[] body = source.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    server.start();
    var config = new ObjectMapper().readValue("""
        {"realm":"CEDAR", "authServerUrl":"%s"}
        """.formatted(base), KeycloakConfig.class);
    verifier = new KeycloakDeploymentProvider().buildDeployment(config, clock::get);
  }

  @AfterEach
  void stop() throws Exception {
    verifier.close();
    server.stop(0);
  }

  @Test
  void rotationsRefreshOnceAndRetiredKeysExpire() throws Exception {
    assertEquals("user", verifier.verify(token("old", oldKey)).getSubject());
    publish(jwk("new", newKey));
    assertThrows(VerificationException.class, () -> verifier.verify(token("new", newKey)));
    assertEquals(2, requests.get());
    clock.addAndGet(KeycloakTokenVerifier.REFRESH_INTERVAL_NANOS);
    verifier.verify(token("new", newKey));
    assertEquals(4, requests.get());
    assertThrows(VerificationException.class, () -> verifier.verify(token("old", oldKey)));
    assertEquals(4, requests.get());
    clock.addAndGet(KeycloakTokenVerifier.KEY_LIFETIME_NANOS);
    publish(jwk("old", oldKey));
    assertThrows(VerificationException.class, () -> verifier.verify(token("new", newKey)));
    verifier.verify(token("old", oldKey));
    assertEquals(6, requests.get());
  }

  @Test
  void cachedKeysWorkDuringAnOutageButExpiredKeysDoNot() throws Exception {
    String token = token("old", oldKey);
    verifier.verify(token);
    status = 503;
    assertDoesNotThrow(() -> verifier.verify(token));
    clock.addAndGet(KeycloakTokenVerifier.KEY_LIFETIME_NANOS);
    var failure = assertThrows(ServiceUnavailableException.class, () -> verifier.verify(token));
    assertEquals(503, failure.getResponse().getStatus());
    assertTrue(failure.getMessage().contains(issuer));
    assertThrows(ServiceUnavailableException.class, () -> verifier.verify(token));
    assertEquals(3, requests.get(), "failures are throttled too");
    status = 200;
    clock.addAndGet(KeycloakTokenVerifier.REFRESH_INTERVAL_NANOS);
    assertDoesNotThrow(() -> verifier.verify(token));
  }

  @Test
  void anUnknownKeyCannotForceAnUnboundedNumberOfRequests() throws Exception {
    for (int i = 0; i < 25; i++) {
      String forged = token("unknown-" + i, newKey);
      assertThrows(VerificationException.class, () -> verifier.verify(forged));
    }
    assertEquals(2, requests.get());
    assertDoesNotThrow(() -> verifier.verify(token("old", oldKey)));
    assertEquals(2, requests.get());
  }

  @Test
  void aFailedUnknownKeyRefreshDoesNotDiscardKnownKeys() throws Exception {
    verifier.verify(token("old", oldKey));
    clock.addAndGet(KeycloakTokenVerifier.REFRESH_INTERVAL_NANOS);
    status = 503;
    assertThrows(ServiceUnavailableException.class, () -> verifier.verify(token("new", newKey)));
    assertDoesNotThrow(() -> verifier.verify(token("old", oldKey)));
    assertThrows(ServiceUnavailableException.class, verifier::verifySigningKeys);
  }

  @Test
  void rejectsAlgorithmConfusionMissingKidsAndWrongKeyAlgorithms() throws Exception {
    var claims = claims();
    for (String token : List.of(new JWSBuilder().kid("old").jsonContent(claims).none(),
        new JWSBuilder().kid("old").jsonContent(claims).hmac256(oldKey.getPublic().getEncoded()),
        new JWSBuilder().jsonContent(claims).rsa256(oldKey.getPrivate()))) {
      assertThrows(VerificationException.class, () -> verifier.verify(token));
    }
    assertEquals(0, requests.get());
    JWK wrongAlgorithm = jwk("old", oldKey);
    wrongAlgorithm.setAlgorithm("RS512");
    publish(wrongAlgorithm);
    assertThrows(VerificationException.class, () -> verifier.verify(token("old", oldKey)));
  }

  @Test
  void discoveryCannotChangeTheIssuerOrSendUsToAnotherOrigin() throws Exception {
    for (String body : List.of("{}", "null", "not json",
        "{\"issuer\":\"https://other.example/realms/CEDAR\"}",
        JsonSerialization.writeValueAsString(Map.of("issuer", issuer, "jwks_uri", "https://other.example/certs")),
        JsonSerialization.writeValueAsString(Map.of("issuer", issuer, "jwks_uri", "bad uri")),
        JsonSerialization.writeValueAsString(Map.of("issuer", issuer)))) {
      discovery = body;
      assertThrows(ServiceUnavailableException.class, verifier::verifySigningKeys);
    }
  }

  @Test
  void rejectsEmptyMalformedAmbiguousOrUnusableKeySets() throws Exception {
    for (String body : List.of("{}", "null", "not json", "{\"keys\":[]}", "{\"keys\":[null]}",
        "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"kid\":\"bad\"}]}",
        " ".repeat(1024 * 1024 + 1))) {
      keys = body;
      assertThrows(ServiceUnavailableException.class, verifier::verifySigningKeys);
    }
    publish(jwk("old", oldKey), jwk("old", newKey));
    assertThrows(ServiceUnavailableException.class, verifier::verifySigningKeys);
    JWK missingKid = jwk("old", oldKey);
    missingKid.setKeyId(null);
    for (JWK invalid : Arrays.asList(
        JWKBuilder.create().kid("enc").rsa(oldKey.getPublic(), KeyUse.ENC),
        missingKid,
        JWKBuilder.create().kid("unsupported").algorithm("PS256").rsa(oldKey.getPublic(), KeyUse.SIG))) {
      publish(invalid);
      assertThrows(ServiceUnavailableException.class, verifier::verifySigningKeys);
    }
  }

  @Test
  void anUnspecifiedKeyAlgorithmStillRequiresAValidSignature() throws Exception {
    JWK key = jwk("old", oldKey);
    key.setAlgorithm(null);
    publish(key);
    verifier.verify(token("old", oldKey));
    assertThrows(VerificationException.class, () -> verifier.verify(token("old", newKey)));
  }

  @Test
  void notYetValidWrongTypeAndMissingSubjectAreRefused() {
    AccessToken future = claims();
    future.nbf(Instant.now().plusSeconds(60).getEpochSecond());
    AccessToken idToken = claims();
    idToken.type("ID");
    AccessToken anonymous = claims();
    anonymous.subject(null);
    for (AccessToken claims : List.of(future, idToken, anonymous)) {
      String token = new JWSBuilder().kid("old").jsonContent(claims).rsa256(oldKey.getPrivate());
      assertThrows(VerificationException.class, () -> verifier.verify(token));
    }
  }

  private AccessToken claims() {
    var token = new AccessToken();
    token.subject("user");
    token.issuer(issuer);
    token.type("Bearer");
    token.issuedNow();
    token.exp(Instant.now().plusSeconds(300).getEpochSecond());
    return token;
  }

  private String token(String kid, KeyPair pair) {
    return new JWSBuilder().kid(kid).jsonContent(claims()).rsa256(pair.getPrivate());
  }

  private JWK jwk(String kid, KeyPair pair) {
    return JWKBuilder.create().kid(kid).algorithm("RS256").rsa(pair.getPublic(), KeyUse.SIG);
  }

  private void publish(JWK... values) throws Exception {
    keys = JsonSerialization.writeValueAsString(Map.of("keys", values));
  }
}
