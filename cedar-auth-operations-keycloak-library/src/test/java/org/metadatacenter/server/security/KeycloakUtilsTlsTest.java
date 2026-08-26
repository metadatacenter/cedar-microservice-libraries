package org.metadatacenter.server.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakUtilsTlsTest {

  @Test
  void adminClientUsesPlatformTlsDefaultsByDefault() {
    KeycloakUtilInfo keycloakUtilInfo = new KeycloakUtilInfo();

    assertFalse(keycloakUtilInfo.isAllowInsecureTls());
    assertNull(KeycloakUtils.buildInsecureSslContext(keycloakUtilInfo.isAllowInsecureTls()));
  }

  @Test
  void adminClientBuildsTrustAllContextOnlyWithExplicitOptIn() {
    KeycloakUtilInfo keycloakUtilInfo = new KeycloakUtilInfo();
    keycloakUtilInfo.setAllowInsecureTls(true);

    assertTrue(keycloakUtilInfo.isAllowInsecureTls());
    assertNotNull(KeycloakUtils.buildInsecureSslContext(keycloakUtilInfo.isAllowInsecureTls()));
  }
}
