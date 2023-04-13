package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.IsBasedOnGroup;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;

public abstract class FolderServerInstanceArtifactCurrentUserReport extends FolderServerArtifactCurrentUserReport
    implements InstanceArtifactWithIsBasedOn {

  private IsBasedOnGroup isBasedOnGroup;
  private FolderServerTemplateExtract isBasedOnExtract;

  public FolderServerInstanceArtifactCurrentUserReport(CedarResourceType resourceType) {
    super(resourceType);
    isBasedOnGroup = new IsBasedOnGroup();
  }

  @Override
  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public CedarTemplateId getIsBasedOn() {
    return isBasedOnGroup.getIsBasedOn();
  }

  @Override
  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public void setIsBasedOn(CedarTemplateId isBasedOn) {
    isBasedOnGroup.setIsBasedOn(isBasedOn);
  }

  @JsonProperty(NodeProperty.OnTheFly.IS_BASED_ON)
  public FolderServerTemplateExtract getIsBasedOnExtract() {
    return isBasedOnExtract;
  }

  @JsonProperty(NodeProperty.OnTheFly.IS_BASED_ON)
  public void setIsBasedOnExtract(FolderServerTemplateExtract isBasedOnExtract) {
    this.isBasedOnExtract = isBasedOnExtract;
  }

}
