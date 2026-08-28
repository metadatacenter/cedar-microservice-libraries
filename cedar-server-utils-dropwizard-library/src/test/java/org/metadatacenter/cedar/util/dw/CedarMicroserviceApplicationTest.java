package org.metadatacenter.cedar.util.dw;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOWED_ORIGINS_PARAM;
import static org.eclipse.jetty.servlets.CrossOriginFilter.ALLOW_CREDENTIALS_PARAM;

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
  void wildcardOriginsNeverAllowCredentials() {
    Map<String, String> defaultParameters = CedarMicroserviceApplication.corsInitParameters(Map.of());
    assertEquals("*", defaultParameters.get(ALLOWED_ORIGINS_PARAM));
    assertEquals("false", defaultParameters.get(ALLOW_CREDENTIALS_PARAM));

    Map<String, String> mixedParameters = CedarMicroserviceApplication.corsInitParameters(Map.of(
        CedarMicroserviceApplication.CORS_ALLOWED_ORIGINS_ENV,
        "https://workspace.example.org,*"));
    assertEquals("false", mixedParameters.get(ALLOW_CREDENTIALS_PARAM));
  }

  @Test
  void exactOriginAllowlistAllowsCredentials() {
    Map<String, String> parameters = CedarMicroserviceApplication.corsInitParameters(Map.of(
        CedarMicroserviceApplication.CORS_ALLOWED_ORIGINS_ENV,
        "https://workspace.example.org,https://designer.example.org"));
    assertEquals("true", parameters.get(ALLOW_CREDENTIALS_PARAM));
  }

  @Test
  void exposesArtifactRevisionHeadersToBrowserClients() {
    assertTrue(CedarMicroserviceApplication.HTTP_EXPOSED_HEADERS.contains("ETag"));
  }
}
