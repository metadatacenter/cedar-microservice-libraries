package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerInstanceReport extends FolderServerInstanceArtifactReport implements InstanceArtifactWithIsBasedOn {

  public FolderServerInstanceReport() {
    super(CedarResourceType.INSTANCE);
  }

}
