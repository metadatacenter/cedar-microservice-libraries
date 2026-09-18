package org.metadatacenter.server.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class InstanceCloneRenamesTest {
  private final JsonNode target;

  InstanceCloneRenamesTest() throws Exception {
    target = JsonMapper.STRICT_MAPPER.readTree("""
        {"properties":{"@context":{"properties":{"Name":{"enum":["https://example/name"]}}},
         "Name":{"type":"object","properties":{"@value":{"type":"string"}}}}}
        """);
  }

  @Test
  void preservesValueAndPropertyIriWhenStudyNameBecomesName() throws Exception {
    ObjectNode instance = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree("""
        {"@context":{"Study Name":"https://example/name"},"Study Name":{"@value":"SDY1"}}
        """);
    InstanceCloneRenames.apply(instance, target);
    assertEquals("SDY1", instance.path("Name").path("@value").asText());
    assertEquals("https://example/name", instance.path("@context").path("Name").asText());
    assertFalse(instance.has("Study Name"));
    assertFalse(instance.path("@context").has("Study Name"));
    ObjectNode once = instance.deepCopy();
    InstanceCloneRenames.apply(instance, target);
    assertEquals(once, instance);
  }

  @Test
  void renamesInsideRepeatedElements() throws Exception {
    ObjectNode schema = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree("""
        {"properties":{"@context":{"properties":{}},"People":{"type":"array"}}}
        """);
    ((ObjectNode) schema.path("properties").path("People")).set("items", target);
    ObjectNode instance = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree("""
        {"@context":{},"People":[{"@context":{"Study Name":"https://example/name"},
          "Study Name":{"@value":"SDY1"}}]}
        """);
    InstanceCloneRenames.apply(instance, schema);
    assertEquals("SDY1", instance.path("People").get(0).path("Name").path("@value").asText());
  }

  @Test
  void refusesToOverwriteAnExistingValue() throws Exception {
    ObjectNode instance = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree("""
        {"@context":{"Study Name":"https://example/name"},"Study Name":{"@value":"SDY1"},
         "Name":{"@value":"other"}}
        """);
    assertThrows(IllegalArgumentException.class, () -> InstanceCloneRenames.apply(instance, target));
    assertEquals("other", instance.path("Name").path("@value").asText());
  }
}
