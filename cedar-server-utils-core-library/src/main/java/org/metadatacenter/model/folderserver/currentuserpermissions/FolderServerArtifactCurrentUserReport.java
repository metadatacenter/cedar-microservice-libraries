package org.metadatacenter.model.folderserver.currentuserpermissions;

import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.DerivedFromGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithDerivedFromData;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;

public abstract class FolderServerArtifactCurrentUserReport extends FolderServerResourceCurrentUserReport implements FilesystemResourceWithCurrentUserPermissions, ResourceWithDerivedFromData {

  protected DerivedFromGroup derivedFromGroup;
  protected boolean open;

  public FolderServerArtifactCurrentUserReport(CedarResourceType resourceType) {
    super(resourceType);
    derivedFromGroup = new DerivedFromGroup();
  }

  @Override
  public CedarUntypedArtifactId getDerivedFrom() {
    return derivedFromGroup.getDerivedFrom();
  }

  @Override
  public void setDerivedFrom(CedarUntypedArtifactId df) {
    derivedFromGroup.setDerivedFrom(df);
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void setOpen(boolean open) {
    this.open = open;
  }

  public CedarArtifactId getResourceId() {
    return CedarArtifactId.build(getId(), getType());
  }

}
