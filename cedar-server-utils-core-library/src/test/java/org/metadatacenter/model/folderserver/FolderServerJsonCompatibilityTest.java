package org.metadatacenter.model.folderserver;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.GraphDbObjectBuilder;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.response.FolderServerCategoryListResponse;
import org.metadatacenter.util.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FolderServerJsonCompatibilityTest {

  @Test
  void tolerantReaderAcceptsAPropertyWrittenByANewerFolderProducer() throws Exception {
    ObjectNode json = folderJson();
    json.put("futureProperty", "newer value");

    FolderServerFolder folder = JsonMapper.TOLERANT_MAPPER.treeToValue(json, FolderServerFolder.class);

    assertEquals("https://repo.example/folders/one", folder.getId());
    assertEquals("Examples", folder.getName());
  }

  @Test
  void strictReaderStillExposesAnUnexpectedFolderProperty() {
    ObjectNode json = folderJson();
    json.put("futureProperty", "newer value");

    assertThrows(UnrecognizedPropertyException.class,
        () -> JsonMapper.STRICT_MAPPER.treeToValue(json, FolderServerFolder.class));
  }

  @Test
  void tolerantReaderAcceptsAnOlderFolderWithoutNewerOptionalProperties() throws Exception {
    ObjectNode json = folderJson();
    json.remove("schema:description");
    json.remove("isOpen");

    FileSystemResource folder = JsonMapper.TOLERANT_MAPPER.treeToValue(json, FileSystemResource.class);

    assertInstanceOf(FolderServerFolder.class, folder);
    assertEquals("Examples", folder.getName());
  }

  @Test
  void responseEnvelopeToleranceComesFromTheSelectedMapper() throws Exception {
    FolderServerCategoryListResponse response = new FolderServerCategoryListResponse();
    response.setTotalCount(3);
    ObjectNode json = (ObjectNode) JsonMapper.MAPPER.valueToTree(response);
    json.put("futurePageProperty", true);

    FolderServerCategoryListResponse read = JsonMapper.TOLERANT_MAPPER.treeToValue(
        json, FolderServerCategoryListResponse.class);

    assertEquals(3, read.getTotalCount());
    assertThrows(UnrecognizedPropertyException.class,
        () -> JsonMapper.STRICT_MAPPER.treeToValue(json, FolderServerCategoryListResponse.class));
  }

  @Test
  void graphDbArtifactReaderUsesTheTolerantPolicy() throws Exception {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId("https://repo.example/templates/one");
    template.setName("Example template");
    ObjectNode json = (ObjectNode) JsonMapper.MAPPER.valueToTree(template);
    json.put("futureStoredProperty", true);

    FolderServerArtifact read = GraphDbObjectBuilder.artifact(new ByteArrayInputStream(
        JsonMapper.MAPPER.writeValueAsString(json).getBytes(StandardCharsets.UTF_8)));

    assertInstanceOf(FolderServerTemplate.class, read);
    assertEquals("Example template", read.getName());
  }

  private static ObjectNode folderJson() {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setId("https://repo.example/folders/one");
    folder.setName("Examples");
    folder.setDescription("Example folder");
    return (ObjectNode) JsonMapper.MAPPER.valueToTree(folder);
  }
}
