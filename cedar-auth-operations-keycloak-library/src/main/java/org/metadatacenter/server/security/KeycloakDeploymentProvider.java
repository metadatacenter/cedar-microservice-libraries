package org.metadatacenter.server.security;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.metadatacenter.config.KeycloakConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeycloakDeploymentProvider {

  private static final Logger log = LoggerFactory.getLogger(KeycloakDeploymentProvider.class);

  /**
   * How long the adapter's client may take to open a connection to Keycloak. Keycloak sits on the same host or
   * network as every server, so two seconds is long for one that is answering at all.
   */
  static final long CONNECTION_TIMEOUT_MILLIS = 2_000;

  /**
   * How long the adapter's client may wait for Keycloak's response once connected. The discovery document and the
   * key set are small, so five seconds leaves room for a Keycloak that is busy rather than gone.
   */
  static final long SOCKET_TIMEOUT_MILLIS = 5_000;

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
    return KeycloakDeploymentBuilder.build(buildAdapterConfig(keycloakConfig));
  }

  AdapterConfig buildAdapterConfig(KeycloakConfig keycloakConfig) {
    AdapterConfig adapterConfig = new AdapterConfig();
    adapterConfig.setRealm(keycloakConfig.getRealm());
    adapterConfig.setAuthServerUrl(keycloakConfig.getAuthServerUrl());
    adapterConfig.setSslRequired(keycloakConfig.getSslRequired());
    adapterConfig.setResource(keycloakConfig.getResource());
    adapterConfig.setPublicClient(keycloakConfig.isPublicClient());
    // These servers only ever verify incoming tokens; they never drive a login, so bearer-only is the
    // correct mode and it avoids needing a client secret for a public client.
    adapterConfig.setBearerOnly(true);
    // The adapter's client waits indefinitely unless told otherwise, and every bearer request that needs the realm's
    // keys waits with it. A host that could not reach Keycloak held each such request for about 30 seconds before
    // answering 500, and every signed-in page looked hung rather than broken. Bounded, the lookup fails while the
    // caller is still waiting.
    adapterConfig.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
    adapterConfig.setSocketTimeout(SOCKET_TIMEOUT_MILLIS);
    boolean allowInsecureTls = keycloakConfig.isAllowInsecureTls();
    adapterConfig.setDisableTrustManager(allowInsecureTls);
    adapterConfig.setAllowAnyHostname(allowInsecureTls);
    if (allowInsecureTls) {
      log.warn("Keycloak certificate and hostname verification are disabled by explicit development opt-in");
    }
    return adapterConfig;
  }

}
