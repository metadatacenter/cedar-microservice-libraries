package org.metadatacenter.server.security;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.representations.adapters.config.AdapterConfig;
import org.metadatacenter.config.KeycloakConfig;

public class KeycloakDeploymentProvider {

  public KeycloakDeploymentProvider() {
  }

  public KeycloakDeployment buildDeployment(KeycloakConfig keycloakConfig) {
    KeycloakDeployment keycloakDeployment = new KeycloakDeployment();
    keycloakDeployment.setRealm(keycloakConfig.getRealm());
    AdapterConfig adapterConfig = new AdapterConfig();
    adapterConfig.setAuthServerUrl(keycloakConfig.getAuthServerUrl());
    keycloakDeployment.setAuthServerBaseUrl(adapterConfig);
    keycloakDeployment.setSslRequired(SslRequired.valueOf(keycloakConfig.getSslRequired()));
    keycloakDeployment.setResourceName(keycloakConfig.getResource());
    keycloakDeployment.setPublicClient(keycloakConfig.isPublicClient());
    return keycloakDeployment;
  }

}
