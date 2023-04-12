package org.metadatacenter.model.folderserver.datagroup;

import org.metadatacenter.id.CedarUntypedSchemaArtifactId;

public class PreviousVersionGroup {

  protected CedarUntypedSchemaArtifactId previousVersion;

  public PreviousVersionGroup() {
  }

  public CedarUntypedSchemaArtifactId getPreviousVersion() {
    return previousVersion;
  }

  public void setPreviousVersion(CedarUntypedSchemaArtifactId previousVersion) {
    this.previousVersion = previousVersion;
  }

}
