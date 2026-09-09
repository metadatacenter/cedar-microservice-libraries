package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.metadatacenter.id.CedarInstanceArtifactId;
import org.metadatacenter.model.CedarResourceType;

public class FolderServerTemplateInstanceExtract extends FolderServerInstanceArtifactExtract {

  public FolderServerTemplateInstanceExtract() {
    super(CedarResourceType.INSTANCE);
  }

  @Override
  @JsonIgnore
  public CedarInstanceArtifactId getResourceId() {
    return CedarInstanceArtifactId.build(this.getId(), this.getType());
  }
}
