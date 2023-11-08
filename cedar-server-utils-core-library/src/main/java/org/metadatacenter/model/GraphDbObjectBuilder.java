package org.metadatacenter.model;

import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public abstract class GraphDbObjectBuilder {

  private static final Logger log = LoggerFactory.getLogger(GraphDbObjectBuilder.class);

  public static FolderServerArtifact artifact(InputStream stream) throws IOException {
    FileSystemResource folderServerNode = JsonMapper.MAPPER.readValue(stream, FileSystemResource.class);
    return (FolderServerArtifact) folderServerNode;
  }

  public static FolderServerArtifact forResourceType(CedarResourceType resourceType) {
    return switch (resourceType) {
      case TEMPLATE -> new FolderServerTemplate();
      case ELEMENT -> new FolderServerElement();
      case FIELD -> new FolderServerField();
      case INSTANCE -> new FolderServerInstance();
      default -> null;
    };
  }

  public static FolderServerArtifact forResourceType(CedarResourceType resourceType, CedarArtifactId newId, String name, String description,
                                                     String identifier, ResourceVersion version, BiboStatus publicationStatus) {
    FolderServerArtifact r = forResourceType(resourceType);
    r.setId(newId.getId());
    r.setType(resourceType);
    r.setName(name);
    r.setDescription(description);
    r.setIdentifier(identifier);
    if (resourceType.isVersioned()) {
      FolderServerSchemaArtifact a = (FolderServerSchemaArtifact) r;
      a.setVersion(version.getValue());
      a.setPublicationStatus(publicationStatus.getValue());
    }
    return r;
  }
}
