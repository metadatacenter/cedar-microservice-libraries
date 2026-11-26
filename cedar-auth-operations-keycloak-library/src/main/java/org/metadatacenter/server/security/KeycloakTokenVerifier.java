package org.metadatacenter.server.security;

import jakarta.ws.rs.ServiceUnavailableException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.keycloak.TokenVerifier;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKParser;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.representations.AccessToken;
import org.keycloak.util.JsonSerialization;

import java.io.IOException;
import java.net.URI;
import java.security.PublicKey;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Verifies against the configured realm, never an issuer or key URL supplied by a token.
 * Known keys are cached for at most a day, as in the retired adapter; an unknown kid triggers
 * refresh at most once per ten seconds, including failed refreshes, to bound unauthenticated work.
 */
public final class KeycloakTokenVerifier implements AutoCloseable {
  static final long REFRESH_INTERVAL_NANOS = Duration.ofSeconds(10).toNanos();
  static final long KEY_LIFETIME_NANOS = Duration.ofDays(1).toNanos();
  private static final Set<String> ALGORITHMS = Set.of("RS256", "RS384", "RS512");
  private static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
  private final String issuer;
  private final CloseableHttpClient client;
  private final LongSupplier clock;
  private Map<String, SigningKey> keys = Map.of();
  private long fetchedAt;
  private long attemptedAt;
  private boolean attempted;
  private ServiceUnavailableException lastFailure;

  private record SigningKey(PublicKey key, String algorithm) { }

  KeycloakTokenVerifier(String base, String realm, CloseableHttpClient client, LongSupplier clock) {
    this.issuer = KeycloakUriBuilder.fromUri(base).path("/realms/{realm}").build(realm).toString();
    this.client = client;
    this.clock = clock;
  }

  public String getRealmInfoUrl() {
    return issuer;
  }

  public AccessToken verify(String encoded) throws VerificationException {
    // Keycloak 26 no longer includes SUBJECT_EXISTS_CHECK in its defaults; CEDAR resolves every
    // caller from sub and must retain that check across the upgrade.
    var verifier = TokenVerifier.create(encoded, AccessToken.class).withDefaultChecks().realmUrl(issuer)
        .withChecks(TokenVerifier.SUBJECT_EXISTS_CHECK);
    var header = verifier.getHeader();
    String algorithm = header.getAlgorithm() == null ? null : header.getAlgorithm().name();
    if (algorithm == null || !ALGORITHMS.contains(algorithm) || header.getKeyId() == null) {
      throw new VerificationException("An access token must name an RSA signing key and algorithm");
    }
    SigningKey key = signingKey(header.getKeyId());
    if (key == null || (key.algorithm() != null && !algorithm.equals(key.algorithm()))) {
      throw new VerificationException("The realm publishes no matching signing key");
    }
    return verifier.publicKey(key.key()).verify().getToken();
  }

  private synchronized SigningKey signingKey(String kid) {
    long now = clock.getAsLong();
    SigningKey key = keys.get(kid);
    if (key != null && now - fetchedAt < KEY_LIFETIME_NANOS) {
      return key;
    }
    if (!attempted || now - attemptedAt >= REFRESH_INTERVAL_NANOS) {
      attempted = true;
      attemptedAt = now;
      try {
        keys = fetchKeys();
        fetchedAt = clock.getAsLong();
        lastFailure = null;
      } catch (ServiceUnavailableException failure) {
        lastFailure = failure;
      }
    }
    if (lastFailure != null) {
      throw lastFailure;
    }
    return keys.get(kid);
  }

  /** Probe the upstream even when cached keys still allow requests to succeed. */
  public void verifySigningKeys() {
    fetchKeys();
  }

  private Map<String, SigningKey> fetchKeys() {
    String discoveryUrl = issuer + "/.well-known/openid-configuration";
    var discovery = read(discoveryUrl, OIDCConfigurationRepresentation.class);
    if (!issuer.equals(discovery.getIssuer())) {
      throw unavailable(discoveryUrl, new IOException("Discovery issuer differs from the configured realm"));
    }
    String keySetUrl = discovery.getJwksUri();
    if (keySetUrl == null) {
      throw unavailable(discoveryUrl, new IOException("The discovery document names no key set"));
    }
    // Keycloak serves certs on the issuer's origin; discovery cannot redirect us to another service.
    URI origin = URI.create(issuer);
    URI target;
    try {
      target = URI.create(keySetUrl);
      if (!origin.getScheme().equals(target.getScheme()) || !origin.getAuthority().equals(target.getAuthority())
          || target.getUserInfo() != null || target.getFragment() != null) {
        throw new IllegalArgumentException("The key set is outside the configured realm's origin");
      }
    } catch (IllegalArgumentException failure) {
      throw unavailable(discoveryUrl, failure);
    }
    var keySet = read(target.toString(), JSONWebKeySet.class);
    Map<String, SigningKey> result = new HashMap<>();
    try {
      if (keySet.getKeys() != null) {
        for (JWK jwk : keySet.getKeys()) {
          if (jwk == null || !"sig".equals(jwk.getPublicKeyUse()) || !"RSA".equals(jwk.getKeyType())
              || jwk.getKeyId() == null || (jwk.getAlgorithm() != null && !ALGORITHMS.contains(jwk.getAlgorithm()))) {
            continue;
          }
          if (result.putIfAbsent(jwk.getKeyId(), new SigningKey(JWKParser.create(jwk).toPublicKey(),
              jwk.getAlgorithm())) != null) {
            throw new IllegalArgumentException("The key set repeats a signing key identifier");
          }
        }
      }
      if (result.isEmpty()) {
        throw new IllegalArgumentException("The key set holds no supported signing key");
      }
      return Map.copyOf(result);
    } catch (RuntimeException failure) {
      throw unavailable(keySetUrl, failure);
    }
  }

  private <T> T read(String url, Class<T> type) {
    try {
      return client.execute(new HttpGet(url), response -> {
        if (response.getCode() != 200 || response.getEntity() == null) {
          throw new IOException("Keycloak returned HTTP " + response.getCode());
        }
        try (var input = response.getEntity().getContent()) {
          byte[] body = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
          if (body.length > MAX_DOCUMENT_BYTES) {
            throw new IOException("Keycloak document exceeds the size limit");
          }
          T document = JsonSerialization.readValue(body, type);
          if (document == null) {
            throw new IOException("Keycloak returned a null document");
          }
          return document;
        }
      });
    } catch (IOException failure) {
      throw unavailable(url, failure);
    }
  }

  private static ServiceUnavailableException unavailable(String url, Exception cause) {
    return new ServiceUnavailableException("Unable to obtain Keycloak signing keys from " + url, (Long) null, cause);
  }

  @Override
  public void close() throws IOException {
    client.close();
  }
}
