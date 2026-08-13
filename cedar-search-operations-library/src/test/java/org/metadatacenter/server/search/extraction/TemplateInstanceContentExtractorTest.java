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
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.InfoField;

import java.util.List;
import java.util.Optional;
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
  void setUp() {
    extractionUtils = mock(ExtractionUtils.class);
    extractor = new TemplateInstanceContentExtractor(extractionUtils);
    requestContext = mock(CedarRequestContext.class);
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
  void actualInstanceShapePreservesValuesWhenTemplateMultiplicityHasChanged() throws Exception {
    ObjectNode template = MAPPER.createObjectNode();
    template.set("declared-repeated-field", field("field-r", "Repeated field", null, true));
    template.set("declared-single-field", field("field-s", "Single field", null, false));
    ObjectNode repeatedElement = element("element-r", "Repeated element", true);
    ((ObjectNode) repeatedElement.get("items")).set("value", field("field-er", "Repeated element value", null,
        false));
    template.set("declared-repeated-element", repeatedElement);
    ObjectNode singleElement = element("element-s", "Single element", false);
    singleElement.set("value", field("field-es", "Single element value", null, false));
    template.set("declared-single-element", singleElement);

    ObjectNode instance = basedOnInstance();
    instance.putObject("declared-repeated-field").put("@value", "stored-single");
    ArrayNode storedRepeated = instance.putArray("declared-single-field");
    storedRepeated.addObject().put("@value", "stored-repeat-1");
    storedRepeated.addObject().put("@value", "stored-repeat-2");
    instance.putObject("declared-repeated-element").putObject("value").put("@value", "element-single");
    ArrayNode storedElements = instance.putArray("declared-single-element");
    storedElements.addObject().putObject("value").put("@value", "element-repeat-1");
    storedElements.addObject().putObject("value").put("@value", "element-repeat-2");
    stubArtifacts(template, instance);

    List<InfoField> output = generateInstanceFields(false);

    assertEquals(List.of("stored-single", "stored-repeat-1", "stored-repeat-2", "element-single",
            "element-repeat-1", "element-repeat-2"),
        output.stream().map(InfoField::getFieldValue).toList());
  }

  static Stream<Arguments> emptyOrMalformedFieldValues() {
    return Stream.of(
        Arguments.of(MAPPER.createObjectNode()),
        Arguments.of(MAPPER.createObjectNode().put("@value", "")),
        Arguments.of(MAPPER.getNodeFactory().nullNode()),
        Arguments.of(MAPPER.getNodeFactory().textNode("not-a-field-object")));
  }

  @ParameterizedTest
  @MethodSource("emptyOrMalformedFieldValues")
  void emptyOrMalformedFieldValuesAreNotAddedToTheSearchIndex(JsonNode storedValue) throws Exception {
    ObjectNode instance = basedOnInstance().set("answer", storedValue);
    stubArtifacts(templateWith("answer", field("field-answer", "Answer", null, false)), instance);

    assertEquals(List.of(), generateInstanceFields(false));
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

  @Test
  void literalDotInFieldKeyDoesNotCollideWithNestedTemplatePath() throws Exception {
    ObjectNode template = MAPPER.createObjectNode();
    template.set("a.b", field("field-flat", "Flat dotted key", null, false));
    ObjectNode nested = element("element-a", "A", false);
    nested.set("b", field("field-nested", "Nested key", null, false));
    template.set("a", nested);
    ObjectNode instance = basedOnInstance();
    instance.putObject("a.b").put("@value", "flat value");
    instance.putObject("a").putObject("b").put("@value", "nested value");
    stubArtifacts(template, instance);

    List<InfoField> output = generateInstanceFields(false);

    assertEquals(List.of("Flat dotted key", "Nested key"),
        output.stream().map(InfoField::getFieldName).toList());
    assertEquals(List.of("['a.b']", "['a']['b']"),
        output.stream().map(InfoField::getFieldPath).toList());
    assertEquals(List.of("flat value", "nested value"),
        output.stream().map(InfoField::getFieldValue).toList());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsInstanceMissingOrBlankBasedOnTemplateIdentifierWithDomainError(boolean blankRatherThanMissing)
      throws Exception {
    ObjectNode instance = MAPPER.createObjectNode();
    if (blankRatherThanMissing) {
      instance.put("schema:isBasedOn", "  ");
    }
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext)).thenReturn(Optional.of(instance));

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
    when(extractionUtils.getArtifactById("field-1", CedarResourceType.FIELD, requestContext)).thenReturn(Optional.of(standalone));

    List<String> output = extractor.generateValueSetsURIs(resource(CedarResourceType.FIELD, "field-1"), requestContext);

    assertEquals(List.of("https://example.org/vs/one", "https://example.org/vs/two"), output);
  }

  /**
   * The graph and the artifact server can disagree — most often while a deletion is in flight, and
   * the permission consumer reaches an artifact between the two deletes. The artifact's name, path
   * and permissions all come from the graph and are still worth indexing, so a missing body must
   * cost the content and nothing more. Failing here used to abandon the whole index write, and with
   * it the permission update for every artifact behind this one in the same event.
   */
  @Test
  void aTemplateAbsentFromTheArtifactServerIsIndexedWithoutItsContent() throws Exception {
    when(extractionUtils.getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext))
        .thenReturn(Optional.empty());

    List<InfoField> output = extractor.generateInfoFields(
        resource(CedarResourceType.TEMPLATE, TEMPLATE_ID), requestContext, false);

    assertEquals(List.of(), output);
  }

  @Test
  void anInstanceAbsentFromTheArtifactServerIsIndexedWithoutItsContent() throws Exception {
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext))
        .thenReturn(Optional.empty());

    assertEquals(List.of(), generateInstanceFields(false));
  }

  /** The instance survives its template. Its values cannot be named, but it stays indexed. */
  @Test
  void anInstanceWhoseTemplateIsAbsentIsIndexedWithoutItsContent() throws Exception {
    ObjectNode instance = MAPPER.createObjectNode();
    instance.put("schema:isBasedOn", TEMPLATE_ID);
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext))
        .thenReturn(Optional.of(instance));
    when(extractionUtils.getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext))
        .thenReturn(Optional.empty());

    assertEquals(List.of(), generateInstanceFields(false));
  }

  @Test
  void aFieldAbsentFromTheArtifactServerContributesNoValueSets() throws Exception {
    when(extractionUtils.getArtifactById("field-1", CedarResourceType.FIELD, requestContext))
        .thenReturn(Optional.empty());

    assertEquals(List.of(),
        extractor.generateValueSetsURIs(resource(CedarResourceType.FIELD, "field-1"), requestContext));
  }

  private List<InfoField> generateInstanceFields(boolean regeneration) throws CedarProcessingException {
    return extractor.generateInfoFields(resource(CedarResourceType.INSTANCE, INSTANCE_ID), requestContext, regeneration);
  }

  private void stubArtifacts(ObjectNode template, ObjectNode instance) throws CedarProcessingException {
    when(extractionUtils.getArtifactById(INSTANCE_ID, CedarResourceType.INSTANCE, requestContext)).thenReturn(Optional.of(instance));
    when(extractionUtils.getArtifactById(TEMPLATE_ID, CedarResourceType.TEMPLATE, requestContext)).thenReturn(Optional.of(template));
  }

  private static FileSystemResource resource(CedarResourceType type, String id) {
    FolderServerFolder resource = new FolderServerFolder();
    resource.setType(type);
    resource.setId(id);
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
