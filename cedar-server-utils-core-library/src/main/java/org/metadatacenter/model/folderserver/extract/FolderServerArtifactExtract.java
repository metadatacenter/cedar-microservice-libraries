package org.metadatacenter.model.folderserver.extract;

import org.metadatacenter.model.CedarResourceType;

public abstract class FolderServerArtifactExtract extends FolderServerResourceExtract {

  protected String trustedBy;

  public FolderServerArtifactExtract(CedarResourceType resourceType) {
    super(resourceType);
  }

  public String getTrustedBy() {
    return trustedBy;
  }

  public void setTrustedBy(String trustedBy) {
    this.trustedBy = trustedBy;
  }

}
