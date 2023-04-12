package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FolderServerInstanceArtifactExtract extends FolderServerArtifactExtract implements InstanceArtifactWithIsBasedOn {

  private CedarTemplateId isBasedOn;

  public FolderServerInstanceArtifactExtract(CedarResourceType resourceType) {
    super(resourceType);
  }

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public CedarTemplateId getIsBasedOn() {
    return isBasedOn;
  }

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public void setIsBasedOn(CedarTemplateId isBasedOn) {
    this.isBasedOn = isBasedOn;
  }

}
