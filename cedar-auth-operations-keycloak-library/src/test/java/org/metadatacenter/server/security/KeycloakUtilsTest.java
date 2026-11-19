package org.metadatacenter.server.security;

import org.junit.jupiter.api.Test;
import org.keycloak.common.util.Base64Url;
import org.keycloak.representations.AccessToken;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  /**
   * The configuration is built from an environment this test supplies in full, so it resolves the same
   * values in a bare shell, a developer's shell and CI, and the assertions name the values the
   * configuration should yield rather than reading them back from it.
   */
  @Test
  void theAdministratorsConnectionComesFromTheConfiguration() {
    Map<String, String> environment = completeEnvironmentFor(SystemComponent.ADMIN_TOOL);
    environment.put("CEDAR_HOST", "metadatacenter.test");
    environment.put("CEDAR_ADMIN_USER_PASSWORD", "admin-password-from-the-environment");
    environment.put("CEDAR_ADMIN_USER_API_KEY", "admin-api-key-from-the-environment");
    environment.put("CEDAR_KEYCLOAK_ALLOW_INSECURE_TLS", "true");
    CedarEnvironmentSource.setOverride(environment);
    try {
      CedarConfig config = CedarConfig.buildForEnvironment(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));

      KeycloakUtilInfo info = KeycloakUtils.initKeycloak(config);

      assertEquals("cedar-admin", info.getCedarAdminUserName());
      assertEquals("admin-password-from-the-environment", info.getCedarAdminUserPassword());
      assertEquals("admin-api-key-from-the-environment", info.getCedarAdminUserApiKey());
      assertEquals("admin-cli", info.getKeycloakClientId());
      assertEquals("CEDAR", info.getKeycloakRealmName());
      assertEquals("https://auth.metadatacenter.test", info.getKeycloakBaseURI());
      assertTrue(info.isAllowInsecureTls());
    } finally {
      CedarEnvironmentSource.clearOverride();
    }
  }

  /**
   * A value for every variable the component requires: off for the rate-limit mode, false for a flag,
   * 1000 for a number, and a name of its own for anything else. Optional variables are left unset, so
   * their defaults apply. Nothing is taken from the process environment.
   */
  private static Map<String, String> completeEnvironmentFor(SystemComponent component) {
    Map<String, String> environment = new LinkedHashMap<>();
    for (CedarEnvironmentVariable variable : CedarConfigEnvironmentDescriptor.getVariableNamesFor(component)) {
      if (variable.isOptional()) {
        continue;
      }
      String value;
      if (variable == CedarEnvironmentVariable.CEDAR_RATE_LIMIT_MODE) {
        value = "off";
      } else if (variable.isBoolean()) {
        value = "false";
      } else if (variable.isNumeric()) {
        value = "1000";
      } else {
        value = "test-" + variable.getName().toLowerCase(Locale.ROOT);
      }
      environment.put(variable.getName(), value);
    }
    return environment;
  }
}
