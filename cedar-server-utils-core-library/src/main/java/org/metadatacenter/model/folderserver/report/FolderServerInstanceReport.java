package org.metadatacenter.model.folderserver.report;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

public class FolderServerInstanceReport extends FolderServerInstanceArtifactReport implements InstanceArtifactWithIsBasedOn {

  public FolderServerInstanceReport() {
    super(CedarResourceType.INSTANCE);
  }

}
