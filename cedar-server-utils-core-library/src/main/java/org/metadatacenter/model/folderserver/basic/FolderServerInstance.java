package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerInstance extends FolderServerInstanceArtifact implements InstanceArtifactWithIsBasedOn {

  public FolderServerInstance() {
    super(CedarResourceType.INSTANCE);
  }

}
