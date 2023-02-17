package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.FolderDataGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithFolderData;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerFolderCurrentUserReport extends FolderServerResourceCurrentUserReport implements ResourceWithFolderData,
    FolderWithCurrentUserPermissions {

  private FolderDataGroup folderDataGroup;

  public FolderServerFolderCurrentUserReport() {
    super(CedarResourceType.FOLDER);
    folderDataGroup = new FolderDataGroup();
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

  public boolean isOpen() {
    return folderDataGroup.isOpen();
  }

  @Override
  public void setOpen(boolean open) {
    this.folderDataGroup.setOpen(open);
  }

}
