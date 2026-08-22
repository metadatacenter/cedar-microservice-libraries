package org.metadatacenter.util.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Round-trip tests for the YAML/JSON conversion of ArtifactYamlTranscoder. The fixtures are copies
 * of cedar-artifact-library test artifacts, so they reflect the artifact forms the library
 * guarantees to read and render.
 */
public class ArtifactYamlTranscoderConversionTest {

  private String readFixture(String name) throws IOException {
    try (InputStream is = getClass().getResourceAsStream("/artifacts/" + name)) {
      assertNotNull(is, "Missing test fixture: " + name);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  public void templateYamlConvertsToJson() throws IOException {
    String yaml = readFixture("SimpleTemplate.yaml");

    String json = ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.TEMPLATE);

    JsonNode node = JsonMapper.MAPPER.readTree(json);
    assertEquals("https://repo.metadatacenter.org/templates/7b8977ed-c4d7-4c29-b202-53e38a41c723",
        node.get("@id").asText());
    assertEquals("Simple Template", node.get("schema:name").asText());
    assertTrue(node.has("properties"), "The JSON Schema form should carry a properties object");
    assertTrue(node.has("@context"), "The JSON-LD form should carry a context");
  }

  @Test
  public void templateJsonConvertsToYaml() throws IOException {
    JsonNode template = JsonMapper.MAPPER.readTree(readFixture("SimpleTemplate.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, false);

    assertNotNull(yaml);
    assertTrue(yaml.contains("Simple Template"));
  }

  @Test
  public void templateJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.MAPPER.readTree(readFixture("SimpleTemplate.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.TEMPLATE, false);
    JsonNode roundTripped =
        JsonMapper.MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.TEMPLATE));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
    assertEquals(original.get("pav:version"), roundTripped.get("pav:version"));
    assertEquals(original.get("bibo:status"), roundTripped.get("bibo:status"));
  }

  @Test
  public void elementJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.MAPPER.readTree(readFixture("element-001.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.ELEMENT, false);
    JsonNode roundTripped =
        JsonMapper.MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.ELEMENT));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
  }

  @Test
  public void fieldJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.MAPPER.readTree(readFixture("StandaloneField.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.FIELD, false);
    JsonNode roundTripped =
        JsonMapper.MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.FIELD));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
  }

  @Test
  public void instanceJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.MAPPER.readTree(readFixture("SimpleInstance.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.INSTANCE, false);
    JsonNode roundTripped =
        JsonMapper.MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.INSTANCE));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:isBasedOn"), roundTripped.get("schema:isBasedOn"));
  }

  @Test
  public void compactYamlIsShorterThanFullYaml() throws IOException {
    JsonNode template = JsonMapper.MAPPER.readTree(readFixture("SimpleTemplate.json"));

    String full = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, false);
    String compact = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, true);

    assertTrue(compact.length() < full.length());
  }

  @Test
  public void malformedYamlIsRejected() {
    assertThrows(Exception.class,
        () -> ArtifactYamlTranscoder.yamlToJsonString("this is: [not: valid template yaml", CedarResourceType.TEMPLATE));
  }

  @Test
  public void minimalYamlIsAccepted() throws IOException {
    // The minimal authoring form: no id, the system supplies the rest
    String minimal = "type: template\n"
        + "name: Minimal Study\n"
        + "children:\n"
        + "- key: study-name\n"
        + "  type: text-field\n"
        + "  name: Study Name\n";

    String json = ArtifactYamlTranscoder.yamlToJsonString(minimal, CedarResourceType.TEMPLATE);

    JsonNode node = JsonMapper.MAPPER.readTree(json);
    assertEquals("Minimal Study", node.get("schema:name").asText());
  }

  /**
   * The compact form transcodes like any other, and names no artifact.
   *
   * <p>It used to be refused here, by its signature: an id with none of the system-recorded keys, since
   * storing it would silently regenerate what it strips. Compact stopped carrying the identifier, which
   * leaves it the same document as the minimal authoring form — so there is nothing left to recognise,
   * and nothing to refuse on a create, where the server supplies what the form omits.
   *
   * <p>What the guard protected is protected by the identifier rule instead: with no id in the body,
   * this document cannot be an update, because an update must name the artifact it updates.
   */
  @Test
  public void compactYamlTranscodesAndNamesNoArtifact() throws IOException {
    JsonNode template = JsonMapper.MAPPER.readTree(readFixture("SimpleTemplate.json"));
    String compact = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, true);
    assertFalse(compact.contains("\nid:"), "compact carries no identifier, which is what made it unrecognisable");

    String json = ArtifactYamlTranscoder.yamlToJsonString(compact, CedarResourceType.TEMPLATE);
    JsonNode node = JsonMapper.MAPPER.readTree(json);

    assertEquals(template.get("schema:name").asText(), node.get("schema:name").asText());
    assertTrue(node.get("@id") == null || node.get("@id").isNull(),
        "and names no artifact, so it can create but never update");
  }

  @Test
  public void yamlOfTheWrongArtifactTypeIsRejected() throws IOException {
    String yaml = readFixture("SimpleTemplate.yaml");
    assertThrows(Exception.class, () -> ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.FIELD));
  }

}
