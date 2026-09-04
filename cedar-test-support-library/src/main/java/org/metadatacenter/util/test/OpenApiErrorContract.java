package org.metadatacenter.util.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Assertions shared by the server suites for the error responses in their committed OpenAPI documents. */
public final class OpenApiErrorContract {

  private static final String CEDAR_ERROR = "#/components/schemas/CedarError";
  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

  private OpenApiErrorContract() {
  }

  /**
   * Asserts that every declared 4xx/5xx response uses the common CEDAR envelope unless its exact
   * method/path/status coordinate is listed as an intentional exception.
   */
  public static void assertDocumented(InputStream input, String... intentionalExceptions) throws IOException {
    Assertions.assertNotNull(input, "generated OpenAPI document");
    JsonNode spec = JsonMapper.MAPPER.readTree(input);
    Assertions.assertTrue(spec.at("/components/schemas/CedarError").isObject(), "CedarError schema");
    Set<String> exceptions = new LinkedHashSet<>(Set.of(intentionalExceptions));

    Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields();
    while (paths.hasNext()) {
      Map.Entry<String, JsonNode> path = paths.next();
      Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().fields();
      while (methods.hasNext()) {
        Map.Entry<String, JsonNode> method = methods.next();
        if (!HTTP_METHODS.contains(method.getKey())) {
          continue;
        }
        Iterator<Map.Entry<String, JsonNode>> responses = method.getValue().path("responses").fields();
        while (responses.hasNext()) {
          Map.Entry<String, JsonNode> response = responses.next();
          if (!response.getKey().matches("[45][0-9][0-9]")) {
            continue;
          }
          String coordinate = method.getKey().toUpperCase() + " " + path.getKey() + " " + response.getKey();
          if (exceptions.remove(coordinate)) {
            continue;
          }

          JsonNode responseNode = resolveResponse(spec, response.getValue());
          String schemaRef = responseNode.path("content").path("application/json").path("schema")
              .path("$ref").asText();
          Assertions.assertEquals(CEDAR_ERROR, schemaRef, coordinate);
        }
      }
    }
    Assertions.assertTrue(exceptions.isEmpty(), "Stale OpenAPI error exceptions: " + exceptions);
  }

  private static JsonNode resolveResponse(JsonNode spec, JsonNode response) {
    String ref = response.path("$ref").asText();
    if (ref.startsWith("#/")) {
      return spec.at(ref.substring(1));
    }
    return response;
  }
}
