package org.metadatacenter.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.metadatacenter.config.KeycloakConfig;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakDeploymentProviderTest {

  private final KeycloakDeploymentProvider provider = new KeycloakDeploymentProvider();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void verifiesCertificatesAndHostnamesByDefault() throws Exception {
    AdapterConfig adapterConfig = provider.buildAdapterConfig(keycloakConfig(false));

    assertFalse(adapterConfig.isDisableTrustManager());
    assertFalse(adapterConfig.isAllowAnyHostname());
    assertTrue(adapterConfig.isBearerOnly());
  }

  @Test
  void disablesBothChecksOnlyWithExplicitOptIn() throws Exception {
    AdapterConfig adapterConfig = provider.buildAdapterConfig(keycloakConfig(true));

    assertTrue(adapterConfig.isDisableTrustManager());
    assertTrue(adapterConfig.isAllowAnyHostname());
  }

  private KeycloakConfig keycloakConfig(boolean allowInsecureTls) throws Exception {
    return objectMapper.readValue("""
        {
          "realm": "CEDAR",
          "authServerUrl": "https://auth.metadatacenter.orgx",
          "sslRequired": "ALL",
          "resource": "cedar-angular-app",
          "publicClient": true,
          "allowInsecureTls": %s
        }
        """.formatted(allowInsecureTls), KeycloakConfig.class);
  }
}
