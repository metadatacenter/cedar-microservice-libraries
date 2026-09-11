package org.metadatacenter.util.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assertions shared by the server suites for the success responses and request bodies in their
 * committed OpenAPI documents.
 *
 * <p>{@link OpenApiErrorContract} covers the 4xx and 5xx side, where one envelope serves the whole
 * estate. A success payload differs per operation, so what can be asserted centrally is that each
 * one is described at all: an operation with no 2xx at all, a 2xx that declares no schema, a
 * {@code $ref} to a schema the document never defines, a schema with no properties, and a
 * structured body typed as a bare string all leave a client with nothing to generate from. The last of those is what an unannotated JAX-RS
 * {@code String} entity parameter produces, which reads as a documented body and describes
 * nothing.</p>
 */
public final class OpenApiSuccessContract {

  private static final Set<String> HTTP_METHODS =
      Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");
  private static final Set<String> BODY_METHODS = Set.of("post", "put", "patch");
  private static final String SCHEMA_PREFIX = "#/components/schemas/";

  private OpenApiSuccessContract() {
  }

  /**
   * Asserts that every operation declares a 2xx, that each 2xx other than 204 describes its own
   * payload, that every schema reference resolves, that no defined schema is an empty object, and
   * that no JSON or YAML payload is typed as a bare string.
   *
   * @param exemptions two forms, each of which must still be true of the document or the assertion
   *                   fails on the stale entry rather than letting it linger.
   *                   A method and path, such as {@code "POST /command/reset-search-index-job"},
   *                   exempts a write whose handler reads no request body.
   *                   A method, path and status, such as {@code "DELETE /templates/{id} 202"},
   *                   exempts a success response that carries no body, which OpenAPI states by
   *                   declaring no content at all. Documenting an empty media type instead would
   *                   promise a client a JSON document of unknown shape.
   */
  public static void assertDescribed(InputStream input, String... exemptions) throws IOException {
    Assertions.assertNotNull(input, "generated OpenAPI document");
    JsonNode spec = JsonMapper.STRICT_MAPPER.readTree(input);
    JsonNode schemas = spec.at("/components/schemas");
    Set<String> allowed = new LinkedHashSet<>();
    Set<String> emptyResponses = new LinkedHashSet<>();
    for (String exemption : exemptions) {
      if (exemption.matches(".* [0-9]{3}$")) {
        emptyResponses.add(exemption);
      } else {
        allowed.add(exemption);
      }
    }
    List<String> findings = new ArrayList<>();

    Iterator<Map.Entry<String, JsonNode>> paths = spec.path("paths").fields();
    while (paths.hasNext()) {
      Map.Entry<String, JsonNode> path = paths.next();
      Iterator<Map.Entry<String, JsonNode>> methods = path.getValue().fields();
      while (methods.hasNext()) {
        Map.Entry<String, JsonNode> method = methods.next();
        if (!HTTP_METHODS.contains(method.getKey())) {
          continue;
        }
        String coordinate = method.getKey().toUpperCase() + " " + path.getKey();
        JsonNode operation = method.getValue();
        checkSuccessResponses(spec, coordinate, operation, emptyResponses, findings);
        if (BODY_METHODS.contains(method.getKey())) {
          checkRequestBody(spec, coordinate, operation, allowed, findings);
        }
      }
    }
    checkSchemas(schemas, findings);
    collectUnresolvedRefs(spec, schemas, findings);

    List<String> distinct = new ArrayList<>(new LinkedHashSet<>(findings));
    Assertions.assertEquals(List.of(), distinct, "Undescribed OpenAPI payloads");
    Assertions.assertTrue(allowed.isEmpty(), "Writes listed as bodiless that now declare a body: " + allowed);
    Assertions.assertTrue(emptyResponses.isEmpty(),
        "Responses listed as empty that no longer exist or now describe a payload: " + emptyResponses);
  }

  private static void checkSuccessResponses(JsonNode spec, String coordinate, JsonNode operation,
                                            Set<String> emptyResponses, List<String> findings) {
    boolean declaresSuccess = false;
    Iterator<Map.Entry<String, JsonNode>> responses = operation.path("responses").fields();
    while (responses.hasNext()) {
      Map.Entry<String, JsonNode> response = responses.next();
      String status = response.getKey();
      if (!status.matches("2[0-9][0-9]")) {
        continue;
      }
      declaresSuccess = true;
      if ("204".equals(status) || emptyResponses.remove(coordinate + " " + status)) {
        continue;
      }
      JsonNode described = resolve(spec, response.getValue());
      if (!described.has("content")) {
        findings.add(coordinate + " " + status + ": describes no payload, so a client has no type "
            + "to return. A response that carries no body belongs on the empty-response list.");
        continue;
      }
      findBareStrings(coordinate + " " + status, described, findings);
    }
    if (!declaresSuccess) {
      findings.add(coordinate + ": declares no 2xx response, so the operation is undocumented");
    }
  }

  private static void checkRequestBody(JsonNode spec, String coordinate, JsonNode operation,
                                       Set<String> allowed, List<String> findings) {
    if (!operation.has("requestBody")) {
      if (!allowed.remove(coordinate)) {
        findings.add(coordinate + ": declares no request body and is not listed as bodiless");
      }
      return;
    }
    findBareStrings(coordinate + " request body", resolve(spec, operation.path("requestBody")), findings);
  }

  private static void findBareStrings(String where, JsonNode payload, List<String> findings) {
    Iterator<Map.Entry<String, JsonNode>> media = payload.path("content").fields();
    while (media.hasNext()) {
      Map.Entry<String, JsonNode> entry = media.next();
      String mediaType = entry.getKey();
      if (!mediaType.equals("application/json") && !mediaType.endsWith("yaml")
          && !mediaType.endsWith("+json")) {
        continue;
      }
      JsonNode schema = entry.getValue().path("schema");
      if (schema.size() == 1 && "string".equals(schema.path("type").asText())) {
        findings.add(where + " (" + mediaType + "): typed as a bare string");
      }
    }
  }

  private static void checkSchemas(JsonNode schemas, List<String> findings) {
    Iterator<Map.Entry<String, JsonNode>> defined = schemas.fields();
    while (defined.hasNext()) {
      Map.Entry<String, JsonNode> entry = defined.next();
      JsonNode schema = entry.getValue();
      boolean isObject = !schema.has("type") || "object".equals(schema.path("type").asText());
      boolean describesSomething = schema.has("properties") || schema.has("additionalProperties")
          || schema.has("allOf") || schema.has("oneOf") || schema.has("anyOf") || schema.has("enum");
      if (isObject && !describesSomething) {
        findings.add("schema " + entry.getKey() + ": defines no properties");
      }
    }
  }

  private static void collectUnresolvedRefs(JsonNode node, JsonNode schemas, List<String> findings) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.asText().startsWith(SCHEMA_PREFIX)) {
        String name = ref.asText().substring(SCHEMA_PREFIX.length());
        if (!schemas.has(name)) {
          findings.add("unresolved schema reference: " + ref.asText());
        }
      }
      node.forEach(child -> collectUnresolvedRefs(child, schemas, findings));
    } else if (node.isArray()) {
      node.forEach(child -> collectUnresolvedRefs(child, schemas, findings));
    }
  }

  private static JsonNode resolve(JsonNode spec, JsonNode node) {
    String ref = node.path("$ref").asText();
    if (ref.startsWith("#/")) {
      return spec.at(ref.substring(1));
    }
    return node;
  }
}
