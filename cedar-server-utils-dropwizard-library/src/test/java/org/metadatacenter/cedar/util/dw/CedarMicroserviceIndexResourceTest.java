package org.metadatacenter.cedar.util.dw;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CedarMicroserviceIndexResourceTest {

  private static final URI BASE = URI.create("https://resource.metadatacenter.org/");

  @Test
  void advertisesBothLinksWhereTheServiceShipsASpec() {
    Map<String, Object> apiDocs = CedarMicroserviceIndexResource.apiDocs(BASE, true);

    assertEquals(URI.create("https://resource.metadatacenter.org/swagger-api/swagger.json"),
        apiDocs.get("swagger.json"));
    assertEquals(URI.create("https://resource.metadatacenter.org/api"), apiDocs.get("swagger-ui"));
  }

  @Test
  void advertisesNothingWhereTheServiceShipsNoSpec() {
    // Ten of the services ship none. Advertising the links anyway sent a caller to a 404 on both.
    assertTrue(CedarMicroserviceIndexResource.apiDocs(BASE, false).isEmpty());
  }

  @Test
  void resolvesTheLinksAgainstTheRequestingHost() {
    // A service is reached under more than one name, so the links cannot be constants.
    Map<String, Object> apiDocs =
        CedarMicroserviceIndexResource.apiDocs(URI.create("https://terminology.example.org/"), true);
    assertEquals(URI.create("https://terminology.example.org/swagger-api/swagger.json"),
        apiDocs.get("swagger.json"));
  }

  @Test
  void handsOutASeparateMapEachTime() {
    // The links differ per caller, and they used to be written into a map shared by every request.
    Map<String, Object> first = CedarMicroserviceIndexResource.apiDocs(BASE, true);
    Map<String, Object> second =
        CedarMicroserviceIndexResource.apiDocs(URI.create("https://user.example.org/"), true);

    assertEquals(URI.create("https://resource.metadatacenter.org/swagger-api/swagger.json"),
        first.get("swagger.json"));
    assertEquals(URI.create("https://user.example.org/swagger-api/swagger.json"),
        second.get("swagger.json"));
  }
}
