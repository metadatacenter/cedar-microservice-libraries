package org.metadatacenter.model.folderserver.datagroup;

import org.metadatacenter.id.CedarUntypedArtifactId;

public class DerivedFromGroup {

  protected CedarUntypedArtifactId derivedFrom;

  public DerivedFromGroup() {
  }

  public CedarUntypedArtifactId getDerivedFrom() {
    return derivedFrom;
  }

  public void setDerivedFrom(CedarUntypedArtifactId derivedFrom) {
    this.derivedFrom = derivedFrom;
  }
}
