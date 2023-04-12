package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerTemplateExtract extends FolderServerSchemaArtifactExtract {

  public FolderServerTemplateExtract() {
    super(CedarResourceType.TEMPLATE);
  }

}
