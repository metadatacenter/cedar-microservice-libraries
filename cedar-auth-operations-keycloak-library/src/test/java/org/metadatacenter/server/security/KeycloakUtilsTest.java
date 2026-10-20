package org.metadatacenter.server.security;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Base64Url;
import org.keycloak.representations.AccessToken;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The claims of a token can be read without verifying it, and the administrator's connection to Keycloak is described
 * by the configuration: who connects, through which client, to which realm at which address.
 */
class KeycloakUtilsTest {

  @Test
  void aTokensClaimsAreReadFromItsPayload() throws Exception {
    String payload = Base64Url.encode("{\"sub\": \"u-1\", \"exp\": 42}".getBytes(StandardCharsets.UTF_8));

    AccessToken unsigned = KeycloakUtils.parseToken("header." + payload, AccessToken.class);
    AccessToken signed = KeycloakUtils.parseToken("header." + payload + ".signature", AccessToken.class);

    assertEquals("u-1", unsigned.getSubject());
    assertEquals(42L, signed.getExp());
  }

  @Test
  void anAbsentTokenHasNoClaimsAndAMalformedOneIsRefused() throws Exception {
    assertNull(KeycloakUtils.parseToken(null, AccessToken.class));
    assertThrows(IllegalArgumentException.class, () -> KeycloakUtils.parseToken("one-part", AccessToken.class));
    assertThrows(IllegalArgumentException.class, () -> KeycloakUtils.parseToken("a.b.c.d", AccessToken.class));
  }

  @Test
  void theAdministratorsConnectionComesFromTheConfiguration() {
    CedarConfig config = CedarConfig.buildForEnvironment(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));

    KeycloakUtilInfo info = KeycloakUtils.initKeycloak(config);

    assertEquals(config.getAdminUserConfig().getUserName(), info.getCedarAdminUserName());
    assertEquals(config.getAdminUserConfig().getPassword(), info.getCedarAdminUserPassword());
    assertEquals(config.getAdminUserConfig().getApiKey(), info.getCedarAdminUserApiKey());
    assertEquals(config.getKeycloakConfig().getClientId(), info.getKeycloakClientId());
    assertEquals(config.getKeycloakConfig().getRealm(), info.getKeycloakRealmName());
    assertEquals(config.getKeycloakConfig().getAuthServerUrl(), info.getKeycloakBaseURI());
    assertEquals(config.getKeycloakConfig().isAllowInsecureTls(), info.isAllowInsecureTls());
  }
}
