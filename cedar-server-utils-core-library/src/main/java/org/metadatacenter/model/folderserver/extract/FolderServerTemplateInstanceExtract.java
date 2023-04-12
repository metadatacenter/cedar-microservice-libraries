package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.id.CedarInstanceArtifactId;
import org.metadatacenter.model.CedarResourceType;

@JsonIgnoreProperties(ignoreUnknown = true)
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
