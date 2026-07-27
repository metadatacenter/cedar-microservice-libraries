package org.metadatacenter.server.security;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.metadatacenter.config.KeycloakConfig;

public class KeycloakDeploymentProvider {

  public KeycloakDeploymentProvider() {
  }

  /**
   * Builds a deployment that can verify a bearer token's signature. The earlier version constructed a
   * {@link KeycloakDeployment} by hand and set only the realm, resource and auth-server base — it never
   * installed a public-key locator, so nothing downstream could check a signature, and the token path
   * trusted whatever payload a caller sent. {@link KeycloakDeploymentBuilder#build(AdapterConfig)}
   * derives the realm and JWKS URLs, installs a rotating {@code JWKPublicKeyLocator}, and builds the
   * HTTP client that fetches the realm's signing keys — which is what makes real verification possible.
   */
  public KeycloakDeployment buildDeployment(KeycloakConfig keycloakConfig) {
    AdapterConfig adapterConfig = new AdapterConfig();
    adapterConfig.setRealm(keycloakConfig.getRealm());
    adapterConfig.setAuthServerUrl(keycloakConfig.getAuthServerUrl());
    adapterConfig.setSslRequired(keycloakConfig.getSslRequired());
    adapterConfig.setResource(keycloakConfig.getResource());
    adapterConfig.setPublicClient(keycloakConfig.isPublicClient());
    // These servers only ever verify incoming tokens; they never drive a login, so bearer-only is the
    // correct mode and it avoids needing a client secret for a public client.
    adapterConfig.setBearerOnly(true);
    // The realm is served over the local self-signed .orgx leaves from the CEDAR CA, so the client that
    // fetches its signing keys must trust them. The admin client (KeycloakUtils.buildKeycloak) already
    // trusts them for the same reason. A production deployment behind a real certificate should drop
    // both of these and let the default trust manager validate the chain — tracked on the roadmap.
    adapterConfig.setDisableTrustManager(true);
    adapterConfig.setAllowAnyHostname(true);
    return KeycloakDeploymentBuilder.build(adapterConfig);
  }

}
