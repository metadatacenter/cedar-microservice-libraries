package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.search.extraction.model.FieldValue;
import org.metadatacenter.server.search.extraction.model.TemplateNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateContentExtractorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final TemplateContentExtractor extractor = new TemplateContentExtractor();

  @Test
  void extractsFieldIdentityLabelsPathAndValueSets() throws Exception {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.set("diagnosis", field("field-1", "Diagnosis", "Preferred diagnosis", false,
        "https://example.org/vs/one", "https://example.org/vs/two"));

    List<TemplateNode> nodes = extractor.getTemplateNodes(schema);

    assertEquals(1, nodes.size());
    TemplateNode node = nodes.get(0);
    assertEquals("field-1", node.getId());
    assertEquals("Diagnosis", node.getName());
    assertEquals("Preferred diagnosis", node.getPrefLabel());
    assertEquals(CedarResourceType.FIELD, node.getType());
    assertEquals(List.of("diagnosis"), node.getPath());
    assertEquals("diagnosis", node.generatePathDotNotation());
    assertEquals("['diagnosis']", node.generatePathBracketNotation());
    assertEquals(List.of("https://example.org/vs/one", "https://example.org/vs/two"), node.getValueSetURIs());
  }

  @Test
  void extractsNestedSingleAndArrayElementsAndFieldsInTraversalOrder() throws Exception {
    ObjectNode address = element("element-address", "Address", false);
    address.set("street", field("field-street", "Street", null, false));
    address.set("phones", field("field-phone", "Phone", null, true));
    ObjectNode visits = element("element-visit", "Visit", true);
    ((ObjectNode) visits.get("items")).set("date", field("field-date", "Date", null, false));
    ObjectNode schema = MAPPER.createObjectNode();
    schema.set("address", address);
    schema.set("visits", visits);

    List<TemplateNode> nodes = extractor.getTemplateNodes(schema);

    assertEquals(List.of("address", "address.street", "address.phones", "visits", "visits.date"),
        nodes.stream().map(TemplateNode::generatePathDotNotation).toList());
    assertEquals(List.of(false, false, true, true, false),
        nodes.stream().map(TemplateNode::isArray).toList());
    assertEquals(List.of(CedarResourceType.ELEMENT, CedarResourceType.FIELD, CedarResourceType.FIELD,
            CedarResourceType.ELEMENT, CedarResourceType.FIELD),
        nodes.stream().map(TemplateNode::getType).toList());
  }

  @Test
  void wrapsStandaloneFieldAtStableFieldPath() throws Exception {
    ObjectNode standalone = field("standalone-id", "Standalone", null, false);

    List<TemplateNode> nodes = extractor.getTemplateNodes(standalone, CedarResourceType.FIELD);

    assertEquals(1, nodes.size());
    assertEquals("field", nodes.get(0).generatePathDotNotation());
    assertEquals("standalone-id", nodes.get(0).getId());
  }

  @Test
  void ignoresMetadataAndOrdinaryContainersWithoutArtifactType() throws Exception {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("schema:name", "Template metadata");
    schema.putObject("_ui").put("order", "ignored");
    schema.putObject("properties").putObject("ordinary").put("type", "string");
    schema.set("actual", field("field-1", "Actual", null, false));

    List<TemplateNode> nodes = extractor.getTemplateNodes(schema);

    assertEquals(1, nodes.size());
    assertEquals("actual", nodes.get(0).generatePathDotNotation());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsFieldWithoutNonEmptyIdentifier(boolean emptyRatherThanMissing) {
    ObjectNode schema = MAPPER.createObjectNode();
    ObjectNode invalid = field("field-id", "Invalid", null, false);
    if (emptyRatherThanMissing) {
      invalid.put("@id", "");
    } else {
      invalid.remove("@id");
    }
    schema.set("invalid", invalid);

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> extractor.getTemplateNodes(schema));

    assertEquals("@id not found for template field", error.getMessage());
  }

  @Test
  void skipsMalformedAndEmptyValueSetEntriesButRetainsValidOnes() throws Exception {
    ObjectNode schema = MAPPER.createObjectNode();
    ObjectNode constrained = field("field-1", "Constrained", null, false);
    ArrayNode valueSets = (ArrayNode) constrained.get("_valueConstraints").get("valueSets");
    valueSets.addObject();
    valueSets.addObject().putNull("uri");
    valueSets.addObject().put("uri", "");
    valueSets.addObject().put("uri", "https://example.org/vs/valid");
    schema.set("constrained", constrained);

    List<TemplateNode> nodes = extractor.getTemplateNodes(schema);

    assertEquals(List.of("https://example.org/vs/valid"), nodes.get(0).getValueSetURIs());
  }

  @Test
  void missingItemsInArrayShapedContainerDoesNotManufactureNodes() throws Exception {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.putObject("broken-array").putNull("items");
    schema.set("valid", field("field-valid", "Valid", null, false));

    List<TemplateNode> nodes = extractor.getTemplateNodes(schema);

    assertEquals(List.of("valid"), nodes.stream().map(TemplateNode::generatePathDotNotation).toList());
  }

  @Test
  void modelPathNotationPreservesKeysWithoutInterpretation() {
    FieldValue value = new FieldValue("x.y", "value", null, List.of("group name", "x.y", "quote'key"));

    assertEquals("group name.x.y.quote'key", value.generatePathDotNotation());
    assertEquals("['group name']['x.y']['quote'key']", value.generatePathBracketNotation());
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, names = {"ELEMENT", "FIELD"})
  void templateNodeAcceptsOnlySchemaComponentTypes(CedarResourceType type) throws Exception {
    TemplateNode node = new TemplateNode("id", "name", "label", List.of("path"), type, true, List.of());

    assertEquals(type, node.getType());
    assertEquals(type == CedarResourceType.FIELD, node.isTemplateFieldNode());
    assertEquals(type == CedarResourceType.ELEMENT, node.isTemplateElementNode());
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, mode = EnumSource.Mode.EXCLUDE, names = {"ELEMENT", "FIELD"})
  void templateNodeRejectsNonComponentTypes(CedarResourceType type) {
    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> new TemplateNode("id", "name", "label", List.of("path"), type, false, List.of()));

    assertEquals("Invalid node type: " + type.name(), error.getMessage());
  }

  private static ObjectNode field(String id, String name, String prefLabel, boolean array, String... valueSetUris) {
    ObjectNode field = MAPPER.createObjectNode();
    field.put("@id", id);
    field.put("@type", CedarResourceType.FIELD.getAtType());
    if (name != null) {
      field.put("schema:name", name);
    }
    if (prefLabel != null) {
      field.put("skos:prefLabel", prefLabel);
    }
    if (valueSetUris.length > 0) {
      ArrayNode valueSets = field.putObject("_valueConstraints").putArray("valueSets");
      for (String uri : valueSetUris) {
        valueSets.addObject().put("uri", uri);
      }
    } else {
      field.putObject("_valueConstraints").putArray("valueSets");
    }
    if (!array) {
      return field;
    }
    return MAPPER.createObjectNode().set("items", field);
  }

  private static ObjectNode element(String id, String name, boolean array) {
    ObjectNode element = MAPPER.createObjectNode();
    element.put("@id", id);
    element.put("@type", CedarResourceType.ELEMENT.getAtType());
    element.put("schema:name", name);
    if (!array) {
      return element;
    }
    return MAPPER.createObjectNode().set("items", element);
  }
}
