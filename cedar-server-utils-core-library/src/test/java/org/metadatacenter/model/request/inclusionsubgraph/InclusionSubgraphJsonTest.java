package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.request.InclusionSubgraphNodeOperation;
import org.metadatacenter.util.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InclusionSubgraphJsonTest {
  @Test
  void previewCanBeSubmittedWithSelectionsIncludingNestedElements() throws Exception {
    InclusionSubgraphTemplate template = new InclusionSubgraphTemplate();
    template.setId("https://repo.example/templates/study");
    template.setName("Study");
    template.setOperation(InclusionSubgraphNodeOperation.UPDATE);
    InclusionSubgraphElement element = new InclusionSubgraphElement();
    element.setId("https://repo.example/template-elements/investigator");
    element.setTemplates(Map.of(template.getId(), template));
    InclusionSubgraphResponse response = new InclusionSubgraphResponse();
    response.setId("https://repo.example/template-fields/name");
    response.setElements(Map.of(element.getId(), element));
    response.setTemplates(Map.of(template.getId(), template));

    String json = JsonMapper.STRICT_MAPPER.writeValueAsString(response);
    assertFalse(json.contains("\"resourceId\""));
    InclusionSubgraphRequest request = JsonMapper.STRICT_MAPPER.readValue(json, InclusionSubgraphRequest.class);
    assertEquals(response.getId(), request.getId());
    assertEquals(InclusionSubgraphNodeOperation.UPDATE,
        request.getElements().get(element.getId()).getTemplates().get(template.getId()).getOperation());
    assertEquals("Study", request.getTemplates().get(template.getId()).getName());

    // A dialog opened before deployment may still carry the former internal property.
    ObjectNode oldPreview = (ObjectNode) JsonMapper.STRICT_MAPPER.readTree(json);
    ((ObjectNode) oldPreview.path("templates").path(template.getId()))
        .putObject("resourceId").put("id", template.getId());
    assertNotNull(JsonMapper.STRICT_MAPPER.treeToValue(oldPreview, InclusionSubgraphRequest.class));
  }
}
