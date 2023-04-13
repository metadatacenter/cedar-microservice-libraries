package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerFolderCurrentUserReport.class, name = CedarResourceType.Types.FOLDER),
    @JsonSubTypes.Type(value = FolderServerFieldCurrentUserReport.class, name = CedarResourceType.Types.FIELD),
    @JsonSubTypes.Type(value = FolderServerElementCurrentUserReport.class, name = CedarResourceType.Types.ELEMENT),
    @JsonSubTypes.Type(value = FolderServerTemplateCurrentUserReport.class, name = CedarResourceType.Types.TEMPLATE),
    @JsonSubTypes.Type(value = FolderServerInstanceCurrentUserReport.class, name = CedarResourceType.Types.INSTANCE)
})
public abstract class FolderServerResourceCurrentUserReport extends FileSystemResource implements FilesystemResourceWithCurrentUserPermissions {

  private CurrentUserResourcePermissions currentUserPermissions = new CurrentUserResourcePermissions();

  public FolderServerResourceCurrentUserReport(CedarResourceType resourceType) {
    super(resourceType);
  }

  public static FolderServerResourceCurrentUserReport fromResource(FileSystemResource resource) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(resource);
      return JsonMapper.MAPPER.readValue(s, FolderServerResourceCurrentUserReport.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.OnTheFly.CURRENT_USER_PERMISSIONS)
  public CurrentUserResourcePermissions getCurrentUserPermissions() {
    return currentUserPermissions;
  }

}
