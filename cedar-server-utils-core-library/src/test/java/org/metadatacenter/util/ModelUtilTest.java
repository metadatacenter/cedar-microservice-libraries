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

import java.util.List;
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
    when(linkedDataUtil.buildNewLinkedDataId(CedarResourceType.ELEMENT)).thenReturn("generated-element-id");
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
  void preservesPermanentFieldIdAndTreatsTheChildAsNewWhenNothingIsStored() throws Exception {
    JsonNode schema = schemaWithProperty("field", "{\"type\":\"object\",\"@id\":\"https://repo.example/fields/1\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = schema.at("/properties/field");
    assertEquals("https://repo.example/fields/1", field.get("@id").textValue());
    assertCreationProvenance(field);
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  // ── child provenance against a stored artifact ───────────────────────────

  private static final String STORED_CHILD = "{\"type\":\"object\",\"@id\":\"https://repo.example/fields/1\","
      + "\"schema:name\":\"Original\","
      + "\"pav:createdOn\":\"2019-01-01T00:00:00Z\",\"pav:createdBy\":\"https://users.example/author\","
      + "\"pav:lastUpdatedOn\":\"2020-06-01T00:00:00Z\",\"oslc:modifiedBy\":\"https://users.example/editor\"}";

  @Test
  void anUnchangedChildKeepsEveryProvenanceValueItHad() throws Exception {
    JsonNode stored = schemaWithProperty("field", STORED_CHILD);
    JsonNode request = schemaWithProperty("field", STORED_CHILD);

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = request.at("/properties/field");
    assertEquals("2019-01-01T00:00:00Z", field.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", field.get("pav:createdBy").textValue());
    assertEquals("2020-06-01T00:00:00Z", field.get("pav:lastUpdatedOn").textValue());
    assertEquals("https://users.example/editor", field.get("oslc:modifiedBy").textValue());
  }

  @Test
  void anUnchangedChildIsUnaffectedByProvenanceTheRequestRestates() throws Exception {
    JsonNode stored = schemaWithProperty("field", STORED_CHILD);
    JsonNode request = schemaWithProperty("field", STORED_CHILD.replace(
        "\"pav:createdBy\":\"https://users.example/author\"",
        "\"pav:createdBy\":\"https://users.example/impostor\""));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("https://users.example/author",
        request.at("/properties/field/pav:createdBy").textValue());
  }

  @Test
  void aChangedChildKeepsItsCreationRecordAndTakesAFreshModificationStamp() throws Exception {
    JsonNode stored = schemaWithProperty("field", STORED_CHILD);
    JsonNode request = schemaWithProperty("field", STORED_CHILD.replace("Original", "Renamed"));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = request.at("/properties/field");
    assertEquals("2019-01-01T00:00:00Z", field.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", field.get("pav:createdBy").textValue());
    assertEquals(provenance.getLastUpdatedOn(), field.get("pav:lastUpdatedOn").textValue());
    assertEquals(provenance.getLastUpdatedBy(), field.get("oslc:modifiedBy").textValue());
  }

  @Test
  void aChildWithNoStoredCounterpartGetsAFullCreationRecord() throws Exception {
    JsonNode stored = schemaWithProperty("field", STORED_CHILD);
    JsonNode request = JsonMapper.MAPPER.readTree("{\"properties\":{\"field\":" + STORED_CHILD + ",\"added\":"
        + "{\"type\":\"object\",\"@id\":\"https://repo.example/fields/2\"}}}");

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertCreationProvenance(request.at("/properties/added"));
  }

  @Test
  void aSiblingIsNotStampedBecauseAnotherChildChanged() throws Exception {
    String other = STORED_CHILD.replace("fields/1", "fields/2").replace("Original", "Untouched");
    JsonNode stored = JsonMapper.MAPPER.readTree(
        "{\"properties\":{\"field\":" + STORED_CHILD + ",\"other\":" + other + "}}");
    JsonNode request = JsonMapper.MAPPER.readTree(
        "{\"properties\":{\"field\":" + STORED_CHILD.replace("Original", "Renamed") + ",\"other\":" + other + "}}");

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals(provenance.getLastUpdatedOn(), request.at("/properties/field/pav:lastUpdatedOn").textValue());
    assertEquals("2020-06-01T00:00:00Z", request.at("/properties/other/pav:lastUpdatedOn").textValue());
  }

  @Test
  void mintingAnIdentifierCountsAsAChange() throws Exception {
    JsonNode stored = schemaWithProperty("field", STORED_CHILD);
    JsonNode request = schemaWithProperty("field", STORED_CHILD.replace("https://repo.example/fields/1", "tmp-9"));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = request.at("/properties/field");
    assertEquals("generated-field-id", field.get("@id").textValue());
    assertEquals("https://users.example/author", field.get("pav:createdBy").textValue());
    assertEquals(provenance.getLastUpdatedBy(), field.get("oslc:modifiedBy").textValue());
  }

  @Test
  void reportsTheIdentifierItReplacedSoTheOriginalIsNotLostSilently() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\",\"@id\":\"tmp-1754932461238-4127\","
        + "\"@type\":\"" + CedarResourceType.AtType.FIELD + "\"}");

    var minted = ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals(1, minted.size());
    assertEquals("child", minted.get(0).property());
    assertEquals("tmp-1754932461238-4127", minted.get(0).replaced());
    assertEquals("generated-field-id", minted.get(0).minted());
    assertTrue(minted.get(0).destroyedAValue(), "a value was destroyed, so it must be reportable");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"@id\":null}"})
  void doesNotClaimToHaveDestroyedAValueWhenTheChildHadNoIdentifier(String idPart) throws Exception {
    String candidate = ("{\"type\":\"object\",\"@type\":\"" + CedarResourceType.AtType.FIELD + "\""
        + (idPart.equals("{}") ? "" : ",\"@id\":null") + "}");
    JsonNode schema = schemaWithProperty("child", candidate);

    var minted = ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals(1, minted.size());
    assertNull(minted.get(0).replaced());
    assertFalse(minted.get(0).destroyedAValue(),
        "nothing was lost, so this must not be reported as a replacement");
  }

  @Test
  void reportsNothingWhenEveryChildIdentifierIsUsable() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\","
        + "\"@id\":\"https://repo.example/template-fields/1\",\"@type\":\""
        + CedarResourceType.AtType.FIELD + "\"}");

    assertTrue(ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil)
        .isEmpty());
  }

  @Test
  void anUnchangedChildCanBeGivenProvenanceTheStoredArtifactLacks() throws Exception {
    // The repair this has to allow: a stored child missing its author, supplied by a request that
    // changes nothing else in that child. Dropping the value here left the artifact unrepairable.
    String storedChild = STORED_CHILD.replaceAll(",\"pav:createdBy\":\"[^\"]*\"", "");
    JsonNode stored = schemaWithProperty("field", storedChild);
    JsonNode request = schemaWithProperty("field", STORED_CHILD);

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode field = request.at("/properties/field");
    assertEquals("https://users.example/author", field.get("pav:createdBy").textValue());
    assertEquals("2020-06-01T00:00:00Z", field.get("pav:lastUpdatedOn").textValue(),
        "the child is otherwise untouched, so its modification stamp does not move");
  }

  /** An element child holding one nested field, so the comparison can be watched below depth 1. */
  private static String elementWith(String nestedProvenance, String nestedTitle) {
    return "{\"type\":\"object\",\"@id\":\"https://repo.example/elements/1\",\"@type\":\""
        + CedarResourceType.AtType.ELEMENT + "\","
        + "\"pav:createdOn\":\"2019-01-01T00:00:00Z\",\"pav:createdBy\":\"https://users.example/author\","
        + "\"pav:lastUpdatedOn\":\"2020-06-01T00:00:00Z\",\"oslc:modifiedBy\":\"https://users.example/editor\","
        + "\"properties\":{\"nested\":{\"type\":\"object\",\"title\":\"" + nestedTitle + "\","
        + "\"@id\":\"https://repo.example/fields/9\",\"@type\":\"" + CedarResourceType.AtType.FIELD + "\","
        + nestedProvenance + "}}}";
  }

  private static final String NESTED_OLD =
      "\"pav:lastUpdatedOn\":\"2020-06-01T00:00:00Z\",\"oslc:modifiedBy\":\"https://users.example/editor\"";
  private static final String NESTED_NEW =
      "\"pav:lastUpdatedOn\":\"2026-08-11T00:00:00Z\",\"oslc:modifiedBy\":\"https://users.example/impostor\"";

  @Test
  void aNestedProvenanceDifferenceIsNotAChangeToTheChildContainingIt() throws Exception {
    JsonNode stored = schemaWithProperty("element", elementWith(NESTED_OLD, "Same"));
    JsonNode request = schemaWithProperty("element", elementWith(NESTED_NEW, "Same"));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("2020-06-01T00:00:00Z", request.at("/properties/element/pav:lastUpdatedOn").textValue(),
        "provenance the request supplied below depth 1 must not move the child's own stamp");
  }

  @Test
  void aNestedContentDifferenceIsStillAChangeToTheChildContainingIt() throws Exception {
    JsonNode stored = schemaWithProperty("element", elementWith(NESTED_OLD, "Before"));
    JsonNode request = schemaWithProperty("element", elementWith(NESTED_OLD, "After"));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals(provenance.getLastUpdatedOn(), request.at("/properties/element/pav:lastUpdatedOn").textValue(),
        "an edit to a nested field is a change to the child containing it");
    assertEquals("https://users.example/author",
        request.at("/properties/element/pav:createdBy").textValue(),
        "and the child keeps the author it had");
  }

  @Test
  void restoresNestedProvenanceFromTheStoredArtifact() throws Exception {
    JsonNode stored = schemaWithProperty("element", elementWith(NESTED_OLD, "Same"));
    JsonNode request = schemaWithProperty("element", elementWith(NESTED_NEW, "Same"));

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("https://users.example/editor",
        request.at("/properties/element/properties/nested/oslc:modifiedBy").textValue(),
        "request-supplied provenance is not authoritative at any schema depth");
  }

  @Test
  void comparesMultiInstanceChildrenThroughTheirItems() throws Exception {
    String wrapped = "{\"type\":\"array\",\"minItems\":0,\"items\":" + STORED_CHILD + "}";
    JsonNode stored = schemaWithProperty("field", wrapped);
    JsonNode request = schemaWithProperty("field", wrapped);

    ModelUtil.ensureFieldIdsRecursively(request, stored, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("2020-06-01T00:00:00Z",
        request.at("/properties/field/items/pav:lastUpdatedOn").textValue());
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

  @Test
  void assignsIdsAndCreationProvenanceToNestedChildren() throws Exception {
    JsonNode schema = schemaWithProperty("element", "{\"type\":\"object\",\"@type\":\""
        + CedarResourceType.AtType.ELEMENT + "\",\"properties\":{\"nested\":{\"type\":\"object\",\"@id\":null,"
        + "\"@type\":\"" + CedarResourceType.AtType.FIELD + "\"}}}");

    var minted = ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    JsonNode nested = schema.at("/properties/element/properties/nested");
    assertEquals("generated-field-id", nested.get("@id").textValue());
    assertCreationProvenance(nested);
    assertTrue(minted.stream().anyMatch(id -> id.property().equals("element/nested")),
        "the audit record identifies the nested property that was normalized");
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
  void mintsAnElementIdForAChildDeclaringItselfAnElement() throws Exception {
    JsonNode schema = schemaWithProperty("child",
        "{\"type\":\"object\",\"@type\":\"" + CedarResourceType.AtType.ELEMENT + "\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-element-id", schema.at("/properties/child/@id").textValue());
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  @Test
  void mintsAFieldIdForAChildDeclaringItselfAField() throws Exception {
    JsonNode schema = schemaWithProperty("child",
        "{\"type\":\"object\",\"@type\":\"" + CedarResourceType.AtType.FIELD + "\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-field-id", schema.at("/properties/child/@id").textValue());
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.ELEMENT);
  }

  @Test
  void mintsAnElementIdForAMultiInstanceElementChild() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"@type\":\""
        + CedarResourceType.AtType.ELEMENT + "\"}}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-element-id", schema.at("/properties/child/items/@id").textValue());
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"type\":\"array\"}", "{\"type\":\"array\",\"items\":null}",
      "{\"type\":\"array\",\"items\":17}", "{\"type\":\"array\",\"items\":[]}"})
  void skipsAMultiInstanceChildWhoseItemsIsNotAnObjectRatherThanFailing(String candidate) throws Exception {
    JsonNode schema = schemaWithProperty("child", candidate);

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.ELEMENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://schema.metadatacenter.org/core/TemplateElement",
      "https://schema.metadatacenter.org/core/TemplateField",
      "https://schema.metadatacenter.org/core/StaticTemplateField"})
  void recognizesEveryChildArtifactTypeTheMetaSchemasAllow(String atType) throws Exception {
    JsonNode child = JsonMapper.MAPPER.readTree("{\"type\":\"object\",\"@type\":\"" + atType + "\"}");

    assertTrue(ModelUtil.hasRecognisedChildType(child));
  }

  @Test
  void mintsAFieldIdForAStaticFieldChild() throws Exception {
    JsonNode schema = schemaWithProperty("logo",
        "{\"type\":\"object\",\"@type\":\"" + CedarResourceType.AtType.STATIC_FIELD + "\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-field-id", schema.at("/properties/logo/@id").textValue());
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.ELEMENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{\"type\":\"object\"}", "{\"type\":\"object\",\"@type\":null}",
      "{\"type\":\"object\",\"@type\":17}",
      "{\"type\":\"object\",\"@type\":\"https://schema.metadatacenter.org/core/Template\"}"})
  void refusesToRecognizeAChildWithoutAUsableType(String candidate) throws Exception {
    JsonNode child = JsonMapper.MAPPER.readTree(candidate);

    assertFalse(ModelUtil.hasRecognisedChildType(child));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://repo.metadatacenter.org/template-fields/1",
      "https://other.example/template-fields/1", "urn:uuid:0d1b0b0a-0000-4000-8000-000000000000"})
  void treatsAnAbsoluteIriAsAUsableChildIdentifier(String id) throws Exception {
    JsonNode child = JsonMapper.MAPPER.readTree("{\"@id\":\"" + id + "\"}");

    assertTrue(ModelUtil.hasUsableChildId(child));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tmp-1754932461238-4127", "TMP-123", " tmp-123", "", "   ",
      "foo", "/template-fields/1", "not a uri at all"})
  void treatsAnythingThatIsNotAnAbsoluteIriAsUnusable(String id) throws Exception {
    JsonNode child = JsonMapper.MAPPER.readTree(JsonMapper.MAPPER.writeValueAsString(
        JsonMapper.MAPPER.createObjectNode().put("@id", id)));

    assertFalse(ModelUtil.hasUsableChildId(child));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"@id\":null}", "{\"@id\":17}", "{\"@id\":{}}", "{\"@id\":[]}"})
  void treatsAnAbsentOrNonStringIdentifierAsUnusable(String candidate) throws Exception {
    assertFalse(ModelUtil.hasUsableChildId(JsonMapper.MAPPER.readTree(candidate)));
  }

  @Test
  void mintsOverAMalformedIdentifierRatherThanKeepingIt() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\",\"@id\":\"not-an-iri\",\"@type\":\""
        + CedarResourceType.AtType.FIELD + "\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("generated-field-id", schema.at("/properties/child/@id").textValue());
  }

  @Test
  void keepsAnIdentifierUnderAForeignBase() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\","
        + "\"@id\":\"https://other.example/template-fields/1\",\"@type\":\""
        + CedarResourceType.AtType.FIELD + "\"}");

    ModelUtil.ensureFieldIdsRecursively(schema, provenance, new ProvenanceUtil(), linkedDataUtil);

    assertEquals("https://other.example/template-fields/1", schema.at("/properties/child/@id").textValue());
    verify(linkedDataUtil, never()).buildNewLinkedDataId(CedarResourceType.FIELD);
  }

  @Test
  void reportsAnElementChildHoldingAFieldIdentifier() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\","
        + "\"@id\":\"https://repo.metadatacenter.org/template-fields/1\",\"@type\":\""
        + CedarResourceType.AtType.ELEMENT + "\"}");

    assertEquals(List.of("child"), ModelUtil.childrenWithMismatchedIdPrefix(schema));
  }

  @Test
  void reportsAFieldChildHoldingAnElementIdentifier() throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\","
        + "\"@id\":\"https://repo.metadatacenter.org/template-elements/1\",\"@type\":\""
        + CedarResourceType.AtType.FIELD + "\"}");

    assertEquals(List.of("child"), ModelUtil.childrenWithMismatchedIdPrefix(schema));
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://repo.metadatacenter.org/template-elements/1",
      "urn:uuid:0d1b0b0a-0000-4000-8000-000000000000"})
  void reportsNoMismatchWhenThePrefixAgreesOrIsAbsent(String id) throws Exception {
    JsonNode schema = schemaWithProperty("child", "{\"type\":\"object\",\"@id\":\"" + id + "\",\"@type\":\""
        + CedarResourceType.AtType.ELEMENT + "\"}");

    assertTrue(ModelUtil.childrenWithMismatchedIdPrefix(schema).isEmpty());
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
