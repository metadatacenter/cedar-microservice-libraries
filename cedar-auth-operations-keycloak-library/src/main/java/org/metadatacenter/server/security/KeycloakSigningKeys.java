package org.metadatacenter.server.security;

/** Health probe using the verifier's own TLS policy, timeouts and signing-key parser. */
public final class KeycloakSigningKeys {
  private KeycloakSigningKeys() { }

  public static void verify(KeycloakTokenVerifier verifier) {
    verifier.verifySigningKeys();
  }
}
