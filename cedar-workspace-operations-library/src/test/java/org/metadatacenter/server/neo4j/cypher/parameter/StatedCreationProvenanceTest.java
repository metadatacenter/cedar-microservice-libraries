package org.metadatacenter.server.neo4j.cypher.parameter;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.server.ArtifactCreationProvenance;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.metadatacenter.util.json.JsonMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** A node registered for a stored document carries the document's times, not a second clock reading. */
class StatedCreationProvenanceTest {
  static FolderServerTemplate template() {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId("https://repo.metadatacenter.org/templates/9d1f0b8e-1f3c-4a2b-9f77-2b1a7c3d4e5f");
    template.setType(CedarResourceType.TEMPLATE);
    template.setName("Template");
    return template;
  }

  static Object stated(Map<String, Object> parameters, NodeProperty property) {
    return parameters.get(Neo4JUtil.escapePropertyName(property.getValue()));
  }

  Map<String, Object> created(ArtifactCreationProvenance provenance) {
    return CypherParamBuilderArtifact.createArtifact(template(),
        CedarFolderId.build("https://repo.metadatacenter.org/folders/f"), provenance).asMap();
  }

  @Test void theNodeTakesTheTimesItsDocumentStates() {
    String createdOn = "2024-06-22T10:15:30.999-07:00";
    String lastUpdatedOn = "2024-06-22T10:15:30.999-07:00";
    var parameters = created(new ArtifactCreationProvenance(createdOn, lastUpdatedOn));
    long second = OffsetDateTime.parse(createdOn).toEpochSecond();
    assertEquals(second, stated(parameters, NodeProperty.CREATED_ON_TS));
    assertEquals(second, stated(parameters, NodeProperty.LAST_UPDATED_ON_TS));
    assertEquals(second, OffsetDateTime.parse((String) stated(parameters, NodeProperty.CREATED_ON)).toEpochSecond());
    assertEquals(second, OffsetDateTime.parse((String) stated(parameters, NodeProperty.LAST_UPDATED_ON)).toEpochSecond());
  }

  @Test void withoutStatedTimesTheNodeStampsTheCurrentTime() {
    long before = Instant.now().getEpochSecond();
    var parameters = created(null);
    assertTrue((long) stated(parameters, NodeProperty.CREATED_ON_TS) >= before);
    assertEquals(stated(parameters, NodeProperty.CREATED_ON), stated(parameters, NodeProperty.LAST_UPDATED_ON));
  }

  @Test void aDocumentThatDoesNotStateBothTimesLeavesTheNodeToStampItsOwn() throws Exception {
    assertNull(ArtifactCreationProvenance.fromDocument(JsonMapper.STRICT_MAPPER.readTree("{}")));
    assertNull(ArtifactCreationProvenance.fromDocument(JsonMapper.STRICT_MAPPER.readTree(
        "{\"pav:createdOn\":\"2024-06-22T10:15:30-07:00\"}")));
    assertNull(ArtifactCreationProvenance.fromDocument(JsonMapper.STRICT_MAPPER.readTree(
        "{\"pav:createdOn\":\"2024-06-22\",\"pav:lastUpdatedOn\":\"2024-06-22T10:15:30-07:00\"}")));
    assertEquals(new ArtifactCreationProvenance("2024-06-22T10:15:30-07:00", "2024-06-23T10:15:30Z"),
        ArtifactCreationProvenance.fromDocument(JsonMapper.STRICT_MAPPER.readTree(
            "{\"pav:createdOn\":\"2024-06-22T10:15:30-07:00\",\"pav:lastUpdatedOn\":\"2024-06-23T10:15:30Z\"}")));
  }
}
