package org.metadatacenter.util.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.artifacts.model.reader.JsonArtifactReader;
import org.metadatacenter.artifacts.model.renderer.JsonArtifactRenderer;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.validation.CedarValidator;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    JsonNode node = JsonMapper.STRICT_MAPPER.readTree(json);
    assertEquals("https://repo.metadatacenter.org/templates/7b8977ed-c4d7-4c29-b202-53e38a41c723",
        node.get("@id").asText());
    assertEquals("Simple Template", node.get("schema:name").asText());
    assertTrue(node.has("properties"), "The JSON Schema form should carry a properties object");
    assertTrue(node.has("@context"), "The JSON-LD form should carry a context");
  }

  @Test
  public void templateJsonConvertsToYaml() throws IOException {
    JsonNode template = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, false);

    assertNotNull(yaml);
    assertTrue(yaml.contains("Simple Template"));
  }

  @Test
  public void templateJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.TEMPLATE, false);
    JsonNode roundTripped =
        JsonMapper.STRICT_MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.TEMPLATE));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
    assertEquals(original.get("pav:version"), roundTripped.get("pav:version"));
    assertEquals(original.get("bibo:status"), roundTripped.get("bibo:status"));
  }

  @Test
  public void elementJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.STRICT_MAPPER.readTree(readFixture("element-001.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.ELEMENT, false);
    JsonNode roundTripped =
        JsonMapper.STRICT_MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.ELEMENT));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
  }

  @Test
  public void fieldJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.STRICT_MAPPER.readTree(readFixture("StandaloneField.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.FIELD, false);
    JsonNode roundTripped =
        JsonMapper.STRICT_MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.FIELD));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:name"), roundTripped.get("schema:name"));
  }

  @Test
  public void instanceJsonSurvivesYamlRoundTrip() throws IOException {
    JsonNode original = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleInstance.json"));

    String yaml = ArtifactYamlTranscoder.jsonToYaml(original, CedarResourceType.INSTANCE, false);
    JsonNode roundTripped =
        JsonMapper.STRICT_MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.INSTANCE));

    assertEquals(original.get("@id"), roundTripped.get("@id"));
    assertEquals(original.get("schema:isBasedOn"), roundTripped.get("schema:isBasedOn"));
  }

  @Test
  public void yamlInstanceIsCompletedAgainstTheTemplateItIsBasedOn() throws IOException {
    JsonNode template = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"));
    String templateIri = template.get("@id").asText();
    String yaml = "type: instance\n"
        + "name: Simple metadata\n"
        + "isBasedOn: " + templateIri + "\n"
        + "children:\n"
        + "  Text Field:\n"
        + "    value: hello\n";
    List<String> requested = new ArrayList<>();

    JsonNode alone = JsonMapper.STRICT_MAPPER.readTree(
        ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.INSTANCE));
    JsonNode completed = JsonMapper.STRICT_MAPPER.readTree(ArtifactYamlTranscoder.yamlToJsonString(yaml,
        CedarResourceType.INSTANCE, iri -> {
          requested.add(iri);
          return template;
        }));

    assertEquals(List.of(templateIri), requested);
    assertFalse(alone.has("Email"), "without a template the YAML's omitted fields stay omitted");
    assertEquals("hello", completed.get("Text Field").get("@value").asText());
    for (JsonNode property : template.get("required")) {
      assertTrue(completed.has(property.asText()), "the completed instance lacks " + property.asText());
    }
  }

  @Test
  public void instanceCompletionIgnoresUnusedLegacyDefaultsWithoutChangingTemplateOrAnswers() throws IOException {
    // Use the current schema rendering: the historic fixture predates array-wrapped multiselect fields.
    ObjectNode template = new JsonArtifactRenderer().renderTemplateSchemaArtifact(new JsonArtifactReader()
        .readTemplateSchemaArtifact((ObjectNode) JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"))));
    var validator = new CedarValidator();
    assertTrue(validator.validateTemplate(template).canSave());
    template.withObject("/properties/Text Field/_valueConstraints")
        .put("regex", "^valid$").put("defaultValue", "invalid");
    template.withObject("/properties/Numeric - Min and Max/_valueConstraints").put("defaultValue", "101");
    template.withObject("/properties/Text Field - Default Value/_valueConstraints")
        .put("defaultValue", "Never seed this default during conversion");
    ObjectNode original = template.deepCopy();
    String yaml = "type: instance\nname: Metadata\nisBasedOn: " + template.path("@id").asText()
        + "\nchildren:\n  Text Field:\n    value: valid\n"
        + "  Numeric - Min and Max:\n    value: '25'\n    datatype: xsd:decimal\n";

    ObjectNode completed = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree(
        ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.INSTANCE, iri -> template));
    assertEquals(original, template, "The resolved template must never be changed");
    assertEquals("valid", completed.at("/Text Field/@value").asText());
    assertEquals("25", completed.at("/Numeric - Min and Max/@value").asText());
    assertTrue(completed.at("/Text Field - Default Value/@value").isNull(), "Omissions stay unanswered");
    assertFalse(validator.validateTemplate(template).canSave(), "Template writes still reject inconsistent defaults");
    var instanceReport = validator.validateTemplateInstance(completed, template);
    assertTrue(instanceReport.canSave(), instanceReport.toString());

    completed.withObject("/Numeric - Min and Max").put("@value", "not a number");
    assertFalse(validator.validateTemplateInstance(completed, template).canSave(), "Malformed answers still fail");
    template.withObject("/properties/Numeric - Min and Max/_valueConstraints").put("maxValue", 1);
    assertThrows(RuntimeException.class,
        () -> ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.INSTANCE, iri -> template),
        "Only defaults are omitted; inconsistent constraint definitions must still fail");
  }

  @Test
  public void compactYamlIsShorterThanFullYaml() throws IOException {
    JsonNode template = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"));

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

    JsonNode node = JsonMapper.STRICT_MAPPER.readTree(json);
    assertEquals("Minimal Study", node.get("schema:name").asText());
  }

  @Test
  public void compactYamlIsRejected() throws IOException {
    // The compact form keeps the id but strips the system-recorded keys; storing it would
    // silently regenerate that content.
    JsonNode template = JsonMapper.STRICT_MAPPER.readTree(readFixture("SimpleTemplate.json"));
    String compact = ArtifactYamlTranscoder.jsonToYaml(template, CedarResourceType.TEMPLATE, true);

    try {
      ArtifactYamlTranscoder.yamlToJsonString(compact, CedarResourceType.TEMPLATE);
      fail("The compact form must be rejected");
    } catch (ArtifactYamlTranscoder.CompactYamlBodyException e) {
      assertTrue(e.getMessage().contains("compact form"));
      assertTrue(e.getMessage().contains("minimal-and-full"));
    }
  }

  @Test
  public void yamlOfTheWrongArtifactTypeIsRejected() throws IOException {
    String yaml = readFixture("SimpleTemplate.yaml");
    assertThrows(Exception.class, () -> ArtifactYamlTranscoder.yamlToJsonString(yaml, CedarResourceType.FIELD));
  }

}
