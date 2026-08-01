package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.InclusionSubgraphNodeOperation;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphElement;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphResponse;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphTemplate;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphTodoElement;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphTodoList;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InclusionSubgraphUtilTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void extractsOnlyFirstLevelFieldAndElementInclusionsAcrossSingleAndArraySchemas() {
    ObjectNode artifact = artifact();
    artifact.withObject("properties").set("single-field", component("field-1", CedarConstants.TEMPLATE_FIELD_TYPE_URI));
    ObjectNode arrayElement = MAPPER.createObjectNode().put("type", "array");
    arrayElement.set("items", component("element-1", CedarConstants.TEMPLATE_ELEMENT_TYPE_URI));
    artifact.withObject("properties").set("array-element", arrayElement);
    artifact.withObject("properties").set("ordinary", MAPPER.createObjectNode().put("type", "string"));
    artifact.withObject("properties").set("unknown-component", component("unknown-1", "https://example.org/Other"));

    List<String> includedIds = updateArcs(artifact);

    assertEquals(List.of("field-1", "element-1"), includedIds);
  }

  @Test
  void nestedChildrenOfIncludedElementAreNotMistakenForFirstLevelArcs() {
    ObjectNode artifact = artifact();
    ObjectNode element = component("element-1", CedarConstants.TEMPLATE_ELEMENT_TYPE_URI);
    element.withObject("properties").set("nested", component("field-nested", CedarConstants.TEMPLATE_FIELD_TYPE_URI));
    artifact.withObject("properties").set("element", element);

    assertEquals(List.of("element-1"), updateArcs(artifact));
  }

  static Stream<Arguments> malformedArtifacts() {
    ObjectNode noProperties = MAPPER.createObjectNode();
    ObjectNode nullProperties = MAPPER.createObjectNode().putNull("properties");
    ObjectNode malformedChildren = artifact();
    malformedChildren.withObject("properties").set("missing-type", MAPPER.createObjectNode().put("@id", "x"));
    malformedChildren.withObject("properties").set("null-type", MAPPER.createObjectNode().putNull("type"));
    malformedChildren.withObject("properties").set("array-no-items", MAPPER.createObjectNode().put("type", "array"));
    malformedChildren.withObject("properties").set("field-no-id",
        MAPPER.createObjectNode().put("type", "object").put("@type", CedarConstants.TEMPLATE_FIELD_TYPE_URI));
    malformedChildren.withObject("properties").put("scalar", "not-an-object");
    return Stream.of(Arguments.of(noProperties), Arguments.of(nullProperties), Arguments.of(malformedChildren));
  }

  @ParameterizedTest
  @MethodSource("malformedArtifacts")
  void malformedOrEmptySchemaClearsInclusionArcsInsteadOfCrashing(JsonNode artifact) {
    assertEquals(List.of(), updateArcs(artifact));
  }

  @Test
  void replacementPreservesRequiredValueAndSynchronizesExistingUiMetadata() throws Exception {
    ObjectNode parent = (ObjectNode) MAPPER.readTree("""
        {
          "properties": {
            "diagnosis": {
              "@id": "field-old",
              "_valueConstraints": {"requiredValue": true}
            }
          },
          "_ui": {
            "propertyLabels": {"diagnosis": "Old label"},
            "propertyDescriptions": {"diagnosis": "Old description"}
          }
        }
        """);
    ObjectNode replacement = component("field-new", CedarConstants.TEMPLATE_FIELD_TYPE_URI)
        .put("schema:name", "New label")
        .put("schema:description", "New description");

    boolean replaced = InclusionSubgraphUtil.updateSubdocumentByAtId(parent, "field-old", replacement);

    assertTrue(replaced);
    assertSame(replacement, parent.path("properties").get("diagnosis"));
    assertTrue(replacement.path("_valueConstraints").path("requiredValue").asBoolean());
    assertEquals("New label", parent.at("/_ui/propertyLabels/diagnosis").asText());
    assertEquals("New description", parent.at("/_ui/propertyDescriptions/diagnosis").asText());
  }

  @Test
  void replacementPreservesExplicitFalseOverReplacementDefaultTrue() {
    ObjectNode parent = artifact();
    ObjectNode old = component("field-old", CedarConstants.TEMPLATE_FIELD_TYPE_URI);
    old.withObject("_valueConstraints").put("requiredValue", false);
    parent.withObject("properties").set("field", old);
    ObjectNode replacement = component("field-new", CedarConstants.TEMPLATE_FIELD_TYPE_URI);
    replacement.withObject("_valueConstraints").put("requiredValue", true);

    assertTrue(InclusionSubgraphUtil.updateSubdocumentByAtId(parent, "field-old", replacement));
    assertFalse(replacement.path("_valueConstraints").path("requiredValue").asBoolean());
  }

  @Test
  void absentOldRequiredValueLeavesReplacementConstraintUnchanged() {
    ObjectNode parent = artifact();
    parent.withObject("properties").set("field", component("field-old", CedarConstants.TEMPLATE_FIELD_TYPE_URI));
    ObjectNode replacement = component("field-new", CedarConstants.TEMPLATE_FIELD_TYPE_URI);
    replacement.withObject("_valueConstraints").put("requiredValue", true);

    assertTrue(InclusionSubgraphUtil.updateSubdocumentByAtId(parent, "field-old", replacement));
    assertTrue(replacement.path("_valueConstraints").path("requiredValue").asBoolean());
  }

  @Test
  void replacesComponentInsideArrayItemsWrapper() {
    ObjectNode parent = artifact();
    ObjectNode array = MAPPER.createObjectNode().put("type", "array");
    array.set("items", component("element-old", CedarConstants.TEMPLATE_ELEMENT_TYPE_URI));
    parent.withObject("properties").set("repeated", array);
    ObjectNode replacement = component("element-new", CedarConstants.TEMPLATE_ELEMENT_TYPE_URI);

    assertTrue(InclusionSubgraphUtil.updateSubdocumentByAtId(parent, "element-old", replacement));
    assertSame(replacement, parent.at("/properties/repeated/items"));
  }

  @Test
  void missingTargetReturnsFalseAndLeavesDocumentUnchanged() {
    ObjectNode parent = artifact();
    parent.withObject("properties").set("field", component("field-1", CedarConstants.TEMPLATE_FIELD_TYPE_URI));
    JsonNode before = parent.deepCopy();

    assertFalse(InclusionSubgraphUtil.updateSubdocumentByAtId(parent, "absent", component("new", CedarConstants.TEMPLATE_FIELD_TYPE_URI)));
    assertEquals(before, parent);
  }

  @Test
  void updatePlanEmitsOnlySelectedNestedElementAndTemplateEdgesWithCorrectSources() {
    InclusionSubgraphResponse response = new InclusionSubgraphResponse();
    response.setId("root-field");
    InclusionSubgraphElement first = element("element-1", InclusionSubgraphNodeOperation.UPDATE);
    InclusionSubgraphElement ignored = element("element-ignored", InclusionSubgraphNodeOperation.DO_NOT_UPDATE);
    InclusionSubgraphElement nested = element("element-2", InclusionSubgraphNodeOperation.UPDATE);
    first.setElements(linkedMap(nested.getId(), nested));
    first.setTemplates(linkedMap("template-1", template("template-1", InclusionSubgraphNodeOperation.UPDATE)));
    nested.setTemplates(linkedMap("template-ignored", template("template-ignored", InclusionSubgraphNodeOperation.DO_NOT_UPDATE)));
    response.setElements(linkedMap(first.getId(), first, ignored.getId(), ignored));
    response.setTemplates(linkedMap("template-root", template("template-root", InclusionSubgraphNodeOperation.UPDATE)));

    InclusionSubgraphTodoList result = InclusionSubgraphUtil.updateResources(response);

    assertEquals(List.of("root-field->element-1", "element-1->element-2", "element-1->template-1",
            "root-field->template-root"),
        result.getTodoList().stream().map(InclusionSubgraphUtilTest::edge).toList());
  }

  private static List<String> updateArcs(JsonNode artifact) {
    FolderServerResourceExtract resource = FolderServerResourceExtract.forType(CedarResourceType.TEMPLATE);
    resource.setId("template-1");
    InclusionSubgraphServiceSession session = mock(InclusionSubgraphServiceSession.class);
    InclusionSubgraphUtil.updateResourceInclusionInfo(resource, session, artifact);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(session).updateInclusionArcs(any(), captor.capture());
    return captor.getValue();
  }

  private static ObjectNode artifact() {
    ObjectNode artifact = MAPPER.createObjectNode();
    artifact.putObject("properties");
    return artifact;
  }

  private static ObjectNode component(String id, String atType) {
    return MAPPER.createObjectNode().put("type", "object").put("@id", id).put("@type", atType);
  }

  private static InclusionSubgraphElement element(String id, InclusionSubgraphNodeOperation operation) {
    InclusionSubgraphElement element = new InclusionSubgraphElement();
    element.setId(id);
    element.setOperation(operation);
    return element;
  }

  private static InclusionSubgraphTemplate template(String id, InclusionSubgraphNodeOperation operation) {
    InclusionSubgraphTemplate template = new InclusionSubgraphTemplate();
    template.setId(id);
    template.setOperation(operation);
    return template;
  }

  private static String edge(InclusionSubgraphTodoElement todo) {
    return todo.getSourceId() + "->" + todo.getTargetId();
  }

  private static <K, V> Map<K, V> linkedMap(K key, V value) {
    Map<K, V> map = new LinkedHashMap<>();
    map.put(key, value);
    return map;
  }

  private static <K, V> Map<K, V> linkedMap(K key1, V value1, K key2, V value2) {
    Map<K, V> map = linkedMap(key1, value1);
    map.put(key2, value2);
    return map;
  }
}
