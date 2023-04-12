package org.metadatacenter.model.folderserver.basic;

import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithIsBasedOn;

public abstract class FolderServerInstanceArtifact extends FolderServerArtifact implements ResourceWithIsBasedOn {

  public FolderServerInstanceArtifact(CedarResourceType resourceType) {
    super(resourceType);
  }

  private CedarTemplateId isBasedOn;

  @Override
  public CedarTemplateId getIsBasedOn() {
    return isBasedOn;
  }

  @Override
  public void setIsBasedOn(CedarTemplateId isBasedOn) {
    this.isBasedOn = isBasedOn;
  }

}
