package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerTemplate extends FolderServerSchemaArtifact {

  public FolderServerTemplate() {
    super(CedarResourceType.TEMPLATE);
  }

}
