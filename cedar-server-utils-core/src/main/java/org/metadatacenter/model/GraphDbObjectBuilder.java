package org.metadatacenter.model;

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
    return (FolderServerArtifact)folderServerNode;
  }

  public static FolderServerArtifact forResourceType(CedarResourceType resourceType) {
    switch (resourceType) {
      case TEMPLATE:
        return new FolderServerTemplate();
      case ELEMENT:
        return new FolderServerElement();
      case FIELD:
        return new FolderServerField();
      case INSTANCE:
        return new FolderServerInstance();
      default:
        return null;
    }
  }

  public static FolderServerArtifact forResourceType(CedarResourceType resourceType, String newId, String name,
                                                     String description, String identifier, ResourceVersion version,
                                                     BiboStatus publicationStatus) {
    FolderServerArtifact r = forResourceType(resourceType);
    r.setId(newId);
    r.setType(resourceType);
    r.setName(name);
    r.setDescription(description);
    r.setIdentifier(identifier);
    if (resourceType.isVersioned()) {
      FolderServerSchemaArtifact a = (FolderServerSchemaArtifact)r;
      a.setVersion(version.getValue());
      a.setPublicationStatus(publicationStatus.getValue());
    }
    return r;
  }
}
