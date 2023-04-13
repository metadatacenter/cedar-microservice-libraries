package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerFolderCurrentUserReport;
import org.metadatacenter.model.folderserver.datagroup.FolderDataGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithFolderData;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithHomeOf;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerFolder extends FileSystemResource implements ResourceWithFolderData, ResourceWithHomeOf {

  private FolderDataGroup folderDataGroup;
  private String homeOf;
  private NodeSharePermission everybodyPermission;

  public FolderServerFolder() {
    super(CedarResourceType.FOLDER);
    this.folderDataGroup = new FolderDataGroup();
  }

  @JsonIgnore
  public CedarFolderId getResourceId() {
    return CedarFolderId.build(getId());
  }

  public static FolderServerFolder fromFolderServerFolderCurrentUserReport(FolderServerFolderCurrentUserReport folder) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(folder);
      return JsonMapper.MAPPER.readValue(s, FolderServerFolder.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public boolean isUserHome() {
    return folderDataGroup.isUserHome();
  }

  @Override
  public void setUserHome(boolean userHome) {
    this.folderDataGroup.setUserHome(userHome);
  }

  @Override
  public boolean isSystem() {
    return folderDataGroup.isSystem();
  }

  @Override
  public void setSystem(boolean system) {
    this.folderDataGroup.setSystem(system);
  }

  @Override
  public boolean isRoot() {
    return folderDataGroup.isRoot();
  }

  @Override
  public void setRoot(boolean root) {
    this.folderDataGroup.setRoot(root);
  }

  @Override
  public boolean isOpen() {
    return folderDataGroup.isOpen();
  }

  @Override
  public void setOpen(boolean open) {
    this.folderDataGroup.setOpen(open);
  }

  @Override
  public String getHomeOf() {
    return homeOf;
  }

  @Override
  public void setHomeOf(String homeOf) {
    this.homeOf = homeOf;
  }

  @Override
  public NodeSharePermission getEverybodyPermission() {
    return everybodyPermission;
  }

  @Override
  public void setEverybodyPermission(NodeSharePermission everybodyPermission) {
    this.everybodyPermission = everybodyPermission;
  }

}
