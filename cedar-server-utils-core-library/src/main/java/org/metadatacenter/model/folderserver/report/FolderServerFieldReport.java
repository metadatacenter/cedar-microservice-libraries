package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerFieldReport extends FolderServerSchemaArtifactReport {

  public FolderServerFieldReport() {
    super(CedarResourceType.FIELD);
  }
}
