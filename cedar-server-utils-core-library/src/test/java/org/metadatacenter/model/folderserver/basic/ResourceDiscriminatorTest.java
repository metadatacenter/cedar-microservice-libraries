package org.metadatacenter.model.folderserver.basic;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.extract.*;
import org.metadatacenter.util.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ResourceDiscriminatorTest {
  @Test void resourcesEmitOneDiscriminatorAndStillReadPolymorphically() throws Exception {
    for (var resource : new FileSystemResource[] {new FolderServerFolder(), new FolderServerField(),
        new FolderServerElement(), new FolderServerTemplate(), new FolderServerInstance()}) {
      String json = JsonMapper.STRICT_MAPPER.writeValueAsString(resource);
      assertEquals(resource.getType().getValue(), JsonMapper.STRICT_MAPPER.readTree(json).get("resourceType").asText());
      assertEquals(resource.getClass(), JsonMapper.TOLERANT_MAPPER.readValue(json, FileSystemResource.class).getClass());
    }
  }

  @Test void extractsEmitOneDiscriminatorAndStillReadPolymorphically() throws Exception {
    for (var resource : new FolderServerResourceExtract[] {new FolderServerFolderExtract(), new FolderServerFieldExtract(),
        new FolderServerElementExtract(), new FolderServerTemplateExtract(), new FolderServerTemplateInstanceExtract()}) {
      String json = JsonMapper.STRICT_MAPPER.writeValueAsString(resource);
      assertNotNull(JsonMapper.STRICT_MAPPER.readTree(json).get("resourceType"));
      assertEquals(resource.getClass(), JsonMapper.STRICT_MAPPER.readValue(json, FolderServerResourceExtract.class).getClass());
    }
  }
}
