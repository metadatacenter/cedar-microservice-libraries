package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerInstanceCurrentUserReport extends FolderServerInstanceArtifactCurrentUserReport {

  public FolderServerInstanceCurrentUserReport() {
    super(CedarResourceType.INSTANCE);
  }

}
