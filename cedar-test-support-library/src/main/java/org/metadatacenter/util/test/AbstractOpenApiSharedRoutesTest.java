package org.metadatacenter.util.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract inherited by every service that generates an OpenAPI document. */
public abstract class AbstractOpenApiSharedRoutesTest {

  private static final List<String> SHARED_PATHS = List.of(
      "/",
      "/healthcheck",
      "/insight/memory",
      "/insight/system",
      "/insight/threads",
      "/insight/gc",
      "/insight/thread-details",
      "/insight/full"
  );

  @Test
  void generatedSpecDocumentsEverySharedRoute() throws IOException {
    JsonNode spec;
    try (InputStream input = getClass().getResourceAsStream("/assets/swagger-api/swagger.json")) {
      assertNotNull(input, "generated OpenAPI document");
      spec = JsonMapper.STRICT_MAPPER.readTree(input);
    }

    for (String path : SHARED_PATHS) {
      JsonNode operation = spec.path("paths").path(path).path("get");
      assertTrue(operation.isObject(), "GET " + path + " is present");
      if (!path.equals("/")) {
        assertTrue(operation.path("security").toString().contains("api_key"),
            "GET " + path + " documents its api_key requirement");
      }
    }
  }
}
