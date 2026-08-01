package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.InfoField;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TemplateInstanceContentExtractorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String TEMPLATE_ID = "template-1";
  private static final String INSTANCE_ID = "instance-1";

  private TemplateInstanceContentExtractor extractor;
  private ExtractionUtils extractionUtils;
  private CedarRequestContext requestContext;

  @BeforeEach
  void setUp() throws Exception {
    extractor = new TemplateInstanceContentExtractor(mock(CedarConfig.class));
    extractionUtils = mock(ExtractionUtils.class);
    requestContext = mock(CedarRequestContext.class);
    Field field = TemplateInstanceContentExtractor.class.getDeclaredField("extractionUtils");
    field.setAccessible(true);
    field.set(extractor, extractionUtils);
  }

  static Stream<Arguments> literalValues() {
    return Stream.of(
        Arguments.of(MAPPER.getNodeFactory().textNode("alpha"), "alpha"),
        Arguments.of(MAPPER.getNodeFactory().numberNode(42), "42"),
        Arguments.of(MAPPER.getNodeFactory().booleanNode(true), "true"));
  }

  @ParameterizedTest
  @MethodSource("literalValues")
  void extractsLiteralValuesWithoutUris(JsonNode value, String expected) throws Exception {
    ObjectNode instance = basedOnInstance();
    instance.putObject("answer").set("@value", value);
    stubArtifacts(templateWith("answer", field("field-answer", "Answer", "Preferred answer", false)), instance);

    InfoField output = generateInstanceFields(false).get(0);

    assertEquals("Answer", output.getFieldName());
    assertEquals("Preferred answer", output.getFieldPrefLabel());
    assertEquals("['answer']", output.getFieldPath());
    assertEquals(expected, output.getFieldValue());
    assertNull(output.getFieldValueUri());
  }

  static Stream<Arguments> ontologyValues() {
    return Stream.of(
        Arguments.of("Diagnosis A", "https://example.org/concept A", "Diagnosis A",
            "https%3A%2F%2Fexample.org%2Fconcept+A"),
        Arguments.of(null, "https://example.org/concept/B", null,
            "https%3A%2F%2Fexample.org%2Fconcept%2FB"),
        Arguments.of("Diagnosis C", null, "Diagnosis C", null));
  }

  @ParameterizedTest
  @MethodSource("ontologyValues")
  void extractsOntologyLabelsAndEncodesUris(String label, String uri, String expectedLabel, String expectedUri)
      throws Exception {
    ObjectNode value = MAPPER.createObjectNode();
    if (label != null) {
      value.put("rdfs:label", label);
    }
    if (uri != null) {
      value.put("@id", uri);
    }
    ObjectNode instance = basedOnInstance().set("term", value);
    stubArtifacts(templateWith("term", field("field-term", "Term", null, false)), instance);

    InfoField output = generateInstanceFields(false).get(0);

    assertEquals(expectedLabel, output.getFieldValue());
    assertEquals(expectedUri, output.getFieldValueUri());
  }

  @Test
  void literalValueTakesPrecedenceOverOntologyFallbackProperties() throws Exception {
    ObjectNode instance = basedOnInstance();
    instance.putObject("term")
        .put("@value", "literal")
        .put("rdfs:label", "ontology label")
        .put("@id", "https://example.org/ignored");
    stubArtifacts(templateWith("term", field("field-term", "Term", null, false)), instance);

    InfoField output = generateInstanceFields(false).get(0);

    assertEquals("literal", output.getFieldValue());
    assertNull(output.getFieldValueUri());
  }

  @Test
  void traversesNestedAndRepeatedElementsAndFieldsInInstanceOrder() throws Exception {
    ObjectNode template = MAPPER.createObjectNode();
    template.set("name", field("field-name", "Name", null, false));
    ObjectNode address = element("element-address", "Address", false);
    address.set("city", field("field-city", "City", null, false));
    template.set("address", address);
    template.set("tags", field("field-tag", "Tag", null, true));
    ObjectNode visits = element("element-visit", "Visit", true);
    ((ObjectNode) visits.get("items")).set("date", field("field-date", "Date", null, false));
    template.set("visits", visits);

    ObjectNode instance = basedOnInstance();
    instance.putObject("name").put("@value", "Ada");
    instance.putObject("address").putObject("city").put("@value", "London");
    ArrayNode tags = instance.putArray("tags");
    tags.addObject().put("@value", "one");
    tags.addObject().put("@value", "two");
    ArrayNode visitValues = instance.putArray("visits");
    visitValues.addObject().putObject("date").put("@value", "2024-01-01");
    visitValues.addObject().putObject("date").put("@value", "2025-02-02");
    stubArtifacts(template, instance);

    List<InfoField> output = generateInstanceFields(false);

    assertEquals(List.of("['name']", "['address']['city']", "['tags']", "['tags']",
            "['visits']['date']", "['visits']['date']"),
        output.stream().map(InfoField::getFieldPath).toList());
    assertEquals(List.of("Ada", "London", "one", "two", "2024-01-01", "2025-02-02"),
        output.stream().map(InfoField::getFieldValue).toList());
  }

  @Test
  void suppressesExactDuplicateInfoFieldsButRetainsDifferentValuesAtSamePath() throws Exception {
    ObjectNode template = templateWith("tags", field("field-tag", "Tag", null, true));
    ObjectNode instance = basedOnInstance();
    ArrayNode tags = instance.putArray("tags");
    tags.addObject().put("@value", "same");
    tags.addObject().put("@value", "same");
    tags.addObject().put("@value", "different");
    stubArtifacts(template, instance);

    List<InfoField> output = generateInstanceFields(false);

    assertEquals(2, output.size());
    assertEquals(List.of("same", "different"), output.stream().map(InfoField::getFieldValue).toList());
  }

  @Test
  void ignoresInstanceMetadataAndFieldsAbsentFromTemplate() throws Exception {
    ObjectNode instance = basedOnInstance();
    instance.put("@id", INSTANCE_ID);
    instance.putObject("unknown").put("@value", "not indexed");
    instance.putObject("known").put("@value", "indexed");
    stubArtifacts(templateWith("known", field("field-known", "Known", null, false)), instance);

    List<InfoField> output = generateInstanceFields(false);

    assertEquals(1, output.size());
    assertEquals("Known", output.get(0).getFieldName());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsInstanceMissingOrBlankBasedOnTemplateIdentifierWithDomainError(boolean blankRatherThanMissing)
      throws Exception {
    ObjectNode instance = MAPPER.createObjectNode();
    if (blankRatherThanMissing) {
      instance.put("schema:isBasedOn", "  ");
    }
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext)).thenReturn(instance);

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> generateInstanceFields(false));

    assertEquals("schema:isBasedOn not found for template instance: " + INSTANCE_ID, error.getMessage());
  }

  @Test
  void regenerationCacheReusesParsedTemplateUntilExplicitlyCleared() throws Exception {
    ObjectNode template = templateWith("known", field("field-known", "Known", null, false));
    ObjectNode instance = basedOnInstance();
    instance.putObject("known").put("@value", "value");
    stubArtifacts(template, instance);

    generateInstanceFields(true);
    generateInstanceFields(true);
    extractor.clearNodesCache();
    generateInstanceFields(true);

    verify(extractionUtils, times(3)).getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext);
    verify(extractionUtils, times(2)).getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext);
  }

  @Test
  void ordinaryIndexingDoesNotRetainTemplateCacheAcrossInstances() throws Exception {
    ObjectNode template = templateWith("known", field("field-known", "Known", null, false));
    ObjectNode instance = basedOnInstance();
    instance.putObject("known").put("@value", "value");
    stubArtifacts(template, instance);

    generateInstanceFields(false);
    generateInstanceFields(false);

    verify(extractionUtils, times(2)).getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext);
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, mode = EnumSource.Mode.EXCLUDE,
      names = {"INSTANCE", "TEMPLATE", "ELEMENT", "FIELD"})
  void rejectsResourceTypesThatCannotSupplySearchFields(CedarResourceType type) {
    FileSystemResource resource = resource(type, "resource-1");

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> extractor.generateInfoFields(resource, requestContext, false));

    assertEquals("The artifact must be an Instance, a Template, an Element, or a Field, but it is a " + type.name(),
        error.getMessage());
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, mode = EnumSource.Mode.EXCLUDE, names = "FIELD")
  void valueSetExtractionRejectsEveryNonFieldResourceType(CedarResourceType type) {
    FileSystemResource resource = resource(type, "resource-1");

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> extractor.generateValueSetsURIs(resource, requestContext));

    assertEquals("The artifact must be a Template Field, but it is a " + type.name(), error.getMessage());
  }

  @Test
  void extractsValueSetsFromStandaloneField() throws Exception {
    ObjectNode standalone = field("field-1", "Field", null, false,
        "https://example.org/vs/one", "https://example.org/vs/two");
    when(extractionUtils.getArtifactById("field-1", CedarResourceType.FIELD, requestContext)).thenReturn(standalone);

    List<String> output = extractor.generateValueSetsURIs(resource(CedarResourceType.FIELD, "field-1"), requestContext);

    assertEquals(List.of("https://example.org/vs/one", "https://example.org/vs/two"), output);
  }

  private List<InfoField> generateInstanceFields(boolean regeneration) throws CedarProcessingException {
    return extractor.generateInfoFields(resource(CedarResourceType.INSTANCE, INSTANCE_ID), requestContext, regeneration);
  }

  private void stubArtifacts(ObjectNode template, ObjectNode instance) throws CedarProcessingException {
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext)).thenReturn(instance);
    when(extractionUtils.getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext)).thenReturn(template);
  }

  private static FileSystemResource resource(CedarResourceType type, String id) {
    FileSystemResource resource = mock(FileSystemResource.class);
    when(resource.getType()).thenReturn(type);
    when(resource.getId()).thenReturn(id);
    return resource;
  }

  private static ObjectNode basedOnInstance() {
    return MAPPER.createObjectNode().put("schema:isBasedOn", TEMPLATE_ID);
  }

  private static ObjectNode templateWith(String key, ObjectNode node) {
    return MAPPER.createObjectNode().set(key, node);
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
    ArrayNode valueSets = field.putObject("_valueConstraints").putArray("valueSets");
    for (String uri : valueSetUris) {
      valueSets.addObject().put("uri", uri);
    }
    return array ? MAPPER.createObjectNode().set("items", field) : field;
  }

  private static ObjectNode element(String id, String name, boolean array) {
    ObjectNode element = MAPPER.createObjectNode();
    element.put("@id", id);
    element.put("@type", CedarResourceType.ELEMENT.getAtType());
    element.put("schema:name", name);
    return array ? MAPPER.createObjectNode().set("items", element) : element;
  }
}
