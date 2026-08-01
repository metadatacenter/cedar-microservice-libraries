package org.metadatacenter.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelUtilTest {

  private LinkedDataUtil linkedDataUtil;
  private ProvenanceInfo provenance;

  @BeforeEach
  void setUp() {
    linkedDataUtil = mock(LinkedDataUtil.class);
    when(linkedDataUtil.buildNewLinkedDataId(CedarResourceType.FIELD)).thenReturn("generated-field-id");
    provenance = new ProvenanceInfo();
    provenance.setCreatedOn("2026-07-31T12:00:00Z");
    provenance.setCreatedBy("https://users.example/creator");
    provenance.setLastUpdatedOn("2026-07-31T13:00:00Z");
    provenance.setLastUpdatedBy("https://users.example/updater");
  }

  @ParameterizedTest
  @ValueSource(strings = {"@id", "@context", "_ui", "_valueConstraints", "schema:name", "schema:description",
      "pav:createdOn", "pav:lastUpdatedOn", "oslc:modifiedBy"})
  void recognizesReservedPropertyPrefixes(String fieldName) {
    assertTrue(ModelUtil.isSpecialField(fieldName));
  }

  @ParameterizedTest
  @ValueSource(strings = {"name", "sample_id", "my@field", "my_schema:name", "Schema:name", "pavilion"})
  void doesNotMistakeOrdinaryFieldNamesForReservedProperties(String fieldName) {
    assertFalse(ModelUtil.isSpecialField(fieldName));
  }

  static Stream<Arguments> metadataExtractors() {
    return Stream.of(
        Arguments.of("@id", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractAtIdFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("schema:name", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractNameFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("schema:description", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractDescriptionFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("schema:identifier", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractIdentifierFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("pav:version", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractVersionFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("bibo:status", (Function<JsonNode, JsonPointerValuePair>) n -> ModelUtil.extractPublicationStatusFromResource(CedarResourceType.TEMPLATE, n)),
        Arguments.of("schema:isBasedOn", (Function<JsonNode, JsonPointerValuePair>) ModelUtil::extractIsBasedOnFromInstance)
    );
  }

  @ParameterizedTest
  @MethodSource("metadataExtractors")
  void extractsAndTrimsTextMetadata(String property, Function<JsonNode, JsonPointerValuePair> extractor) throws Exception {
    JsonNode resource = JsonMapper.MAPPER.readTree("{\"" + property + "\":\"  expected value  \"}");

    JsonPointerValuePair pair = extractor.apply(resource);

    assertEquals("expected value", pair.getValue());
    assertSame(resource.at(pair.getPointer()), resource.get(property));
  }

  @ParameterizedTest
  @MethodSource("metadataExtractors")
  void missingMetadataReturnsThePointerWithoutInventingAValue(String ignoredProperty,
                                                               Function<JsonNode, JsonPointerValuePair> extractor) throws Exception {
    JsonPointerValuePair pair = extractor.apply(JsonMapper.MAPPER.readTree("{}"));

    assertNull(pair.getValue());
    assertTrue(pair.getPointer().startsWith("/"));
  }

  @ParameterizedTest
  @MethodSource("metadataExtractors")
  void nullObjectAndNumericMetadataAreSafelyIgnored(String property,
                                                     Function<JsonNode, JsonPointerValuePair> extractor) throws Exception {
    for (String value : new String[]{"null", "{}", "42"}) {
      JsonNode resource = JsonMapper.MAPPER.readTree("{\"" + property + "\":" + value + "}");
      assertNull(extractor.apply(resource).getValue());
    }
  }

  @Test
  void extractsDoiFromTheAnnotationPath() throws Exception {
    JsonNode resource = JsonMapper.MAPPER.readTree("""
        {"_annotations":{"https://datacite.com/doi":{"@id":"  https://doi.org/10.123/example  "}}}
        """);

    JsonPointerValuePair pair = ModelUtil.extractDOIFromResource(resource);

    assertEquals("https://doi.org/10.123/example", pair.getValue());
  }

  @Test
  void generatesAnIdAndCreationProvenanceForSingleInstanceFieldWithoutId() throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"object\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = schema.at("/properties/field");
    assertEquals("generated-field-id", field.get("@id").textValue());
    assertCreationProvenance(field);
  }

  @Test
  void replacesTemporaryFieldId() throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"object\",\"@id\":\"tmp-123\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-field-id", schema.at("/properties/field/@id").textValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "17", "{}", "[]"})
  void replacesNonStringFieldIdsRatherThanPreservingInvalidJson(String invalidId) throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"object\",\"@id\":" + invalidId + "}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-field-id", schema.at("/properties/field/@id").textValue());
  }

  @Test
  void preservesPermanentFieldIdWhileStillAddingProvenance() throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"object\",\"@id\":\"https://repo.example/fields/1\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = schema.at("/properties/field");
    assertEquals("https://repo.example/fields/1", field.get("@id").textValue());
    assertCreationProvenance(field);
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  @Test
  void appliesIdAndProvenanceToArrayItemsRatherThanTheArrayWrapper() throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"array\",\"items\":{\"type\":\"object\"}}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertNull(schema.at("/properties/field").get("@id"));
    JsonNode item = schema.at("/properties/field/items");
    assertEquals("generated-field-id", item.get("@id").textValue());
    assertCreationProvenance(item);
  }

  @ParameterizedTest
  @ValueSource(strings = {"@context", "_ui", "schema:name", "pav:createdOn", "oslc:modifiedBy"})
  void ignoresReservedObjectPropertiesEvenWhenTheyLookLikeFields(String property) throws Exception {
    JsonNode schema = schemaWithProperty(property, "{\"type\":\"object\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertNull(schema.get("properties").get(property).get("@id"));
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"literal\"", "{\"title\":\"no type\"}", "{\"type\":\"string\"}"})
  void ignoresValuesThatAreNotFieldSchemaObjects(String candidate) throws Exception {
    JsonNode schema = schemaWithProperty("field", candidate);

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  @Test
  void doesNothingWhenPropertiesAreAbsent() throws Exception {
    JsonNode schema = JsonMapper.MAPPER.readTree("{\"type\":\"object\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  private static JsonNode schemaWithProperty(String name, String candidate) throws Exception {
    return JsonMapper.MAPPER.readTree("{\"properties\":{\"" + name + "\":" + candidate + "}}");
  }

  private void assertCreationProvenance(JsonNode field) {
    assertEquals(provenance.getCreatedOn(), field.get("pav:createdOn").textValue());
    assertEquals(provenance.getCreatedBy(), field.get("pav:createdBy").textValue());
    assertEquals(provenance.getLastUpdatedOn(), field.get("pav:lastUpdatedOn").textValue());
    assertEquals(provenance.getLastUpdatedBy(), field.get("oslc:modifiedBy").textValue());
  }
}
