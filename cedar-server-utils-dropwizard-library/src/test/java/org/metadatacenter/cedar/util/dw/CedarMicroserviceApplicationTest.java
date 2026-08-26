package org.metadatacenter.cedar.util.dw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CedarMicroserviceApplicationTest {

  @Test
  void defaultsToWildcardWhenTheVariableIsAbsent() {
    assertEquals("*", CedarMicroserviceApplication.resolveCorsAllowedOrigins(Map.of()));
  }

  @Test
  void defaultsToWildcardWhenTheVariableContainsNoOrigins() {
    assertEquals("*", CedarMicroserviceApplication.resolveCorsAllowedOrigins(
        Map.of(CedarMicroserviceApplication.CORS_ALLOWED_ORIGINS_ENV, " ,  , ")));
  }

  @Test
  void normalizesACommaSeparatedAllowlist() {
    assertEquals(
        "https://workspace.example.org,https://designer.example.org",
        CedarMicroserviceApplication.resolveCorsAllowedOrigins(Map.of(
            CedarMicroserviceApplication.CORS_ALLOWED_ORIGINS_ENV,
            " https://workspace.example.org, ,https://designer.example.org ")));
  }

  @Test
  void exposesArtifactRevisionHeadersToBrowserClients() {
    assertTrue(CedarMicroserviceApplication.HTTP_EXPOSED_HEADERS.contains("ETag"));
  }
}
