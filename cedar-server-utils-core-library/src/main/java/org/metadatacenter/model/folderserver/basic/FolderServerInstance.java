package org.metadatacenter.model.folderserver.basic;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

public class FolderServerInstance extends FolderServerInstanceArtifact implements InstanceArtifactWithIsBasedOn {

  public FolderServerInstance() {
    super(CedarResourceType.INSTANCE);
  }

}
