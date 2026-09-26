package org.metadatacenter.server.search.elasticsearch.service;

import org.junit.jupiter.api.Test;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.util.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class NodePermissionProjectionTest {
  @Test
  void projectionUpdatesOnlyGraphFieldsAndExplicitlyClearsAbsentValues() throws Exception {
    var document = JsonMapper.STRICT_MAPPER.readValue("""
        {"cid":"resource-1", "summaryText":"name", "infoFields":[{"fieldName":"retained content"}],
         "possibleValues":{"valueLabels":["retained value"]}, "computedEverybodyPermission":null}
        """, IndexingDocumentDocument.class);
    var patch = NodeIndexingService.permissionProjectionFields(document);
    assertFalse(patch.has("infoFields"), "permission work must not overwrite indexed artifact content");
    assertFalse(patch.has("possibleValues"));
    assertTrue(patch.has("computedEverybodyPermission"));
    assertTrue(patch.get("computedEverybodyPermission").isNull(), "revocation must clear a previous value");
    assertEquals("name", patch.get("summaryText").asText());
    assertEquals(7, patch.size());
  }
}
