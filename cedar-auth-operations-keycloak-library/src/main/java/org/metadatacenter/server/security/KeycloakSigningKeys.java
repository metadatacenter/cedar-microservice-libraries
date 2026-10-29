package org.metadatacenter.server.security;

import org.apache.http.client.methods.HttpGet;
import org.keycloak.adapters.HttpAdapterUtils;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.constants.ServiceUrlConstants;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.util.JWKSUtils;

/**
 * Whether this server can obtain the realm's signing keys, against which it verifies every bearer token.
 *
 * <p>The adapter cannot answer that question itself. It loads the realm's discovery document lazily, on the first
 * token it verifies, and when the load fails it logs a warning and leaves the key set's URL unset. The next step
 * then fails with a {@code NullPointerException}, so a server that cannot reach Keycloak refuses every signed-in
 * request with a 500 while API keys, which never consult Keycloak, keep working. Nothing about such a server looks
 * unhealthy until someone signs in.
 *
 * <p>{@link #verify} repeats the adapter's two requests through the deployment's own HTTP client, so it shares the
 * client's TLS settings and timeouts, and reports the real cause of a failure rather than the null that follows it.
 * It reads the keys the way verification does, so a key set the verifier could not use fails here too.
 */
public final class KeycloakSigningKeys {

  private KeycloakSigningKeys() {
  }

  /**
   * Fetches the realm's discovery document and the key set it names, and throws unless the set holds a signing key.
   */
  public static void verify(KeycloakDeployment deployment) throws Exception {
    String discoveryUrl = KeycloakUriBuilder.fromUri(deployment.getAuthServerBaseUrl())
        .path(ServiceUrlConstants.DISCOVERY_URL)
        .build(deployment.getRealm())
        .toString();
    OIDCConfigurationRepresentation discovery =
        HttpAdapterUtils.sendJsonHttpRequest(deployment, new HttpGet(discoveryUrl), OIDCConfigurationRepresentation.class);
    String keySetUrl = discovery.getJwksUri();
    if (keySetUrl == null) {
      throw new IllegalStateException("The realm's discovery document at " + discoveryUrl + " names no key set");
    }
    JSONWebKeySet keySet = HttpAdapterUtils.sendJsonHttpRequest(deployment, new HttpGet(keySetUrl), JSONWebKeySet.class);
    if (keySet.getKeys() == null || JWKSUtils.getKeysForUse(keySet, JWK.Use.SIG).isEmpty()) {
      throw new IllegalStateException("The realm's key set at " + keySetUrl + " holds no signing key");
    }
  }
}
