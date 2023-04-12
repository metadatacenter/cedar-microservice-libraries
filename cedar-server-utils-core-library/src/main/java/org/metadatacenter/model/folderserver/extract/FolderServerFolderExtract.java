package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.datagroup.FolderDataGroup;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.FilesystemResourceWithIdAndType;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerFolderExtract extends FolderServerResourceExtract implements FilesystemResourceWithIdAndType {

  private FolderDataGroup folderDataGroup;

  public FolderServerFolderExtract() {
    super(CedarResourceType.FOLDER);
    this.folderDataGroup = new FolderDataGroup();
  }

  public static FolderServerFolderExtract fromFolder(FolderServerFolder folder) {
    try {
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(folder), FolderServerFolderExtract.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  public boolean isUserHome() {
    return folderDataGroup.isUserHome();
  }

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  public void setUserHome(boolean userHome) {
    this.folderDataGroup.setUserHome(userHome);
  }

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  public boolean isSystem() {
    return folderDataGroup.isSystem();
  }

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  public void setSystem(boolean system) {
    this.folderDataGroup.setSystem(system);
  }

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  public boolean isRoot() {
    return folderDataGroup.isRoot();
  }

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  public void setRoot(boolean root) {
    this.folderDataGroup.setRoot(root);
  }

  @Override
  @JsonIgnore
  public CedarFolderId getResourceId() {
    return CedarFolderId.build(this.getId());
  }
}
