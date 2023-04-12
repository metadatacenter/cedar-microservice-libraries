package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerFieldCurrentUserReport extends FolderServerSchemaArtifactCurrentUserReport {

  public FolderServerFieldCurrentUserReport() {
    super(CedarResourceType.FIELD);
  }
}
