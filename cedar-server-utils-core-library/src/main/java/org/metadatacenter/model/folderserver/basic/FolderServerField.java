package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerField extends FolderServerSchemaArtifact {

  public FolderServerField() {
    super(CedarResourceType.FIELD);
  }

}
