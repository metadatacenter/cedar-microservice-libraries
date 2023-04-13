package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithNumberOfInstances;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerTemplateCurrentUserReport extends FolderServerSchemaArtifactCurrentUserReport
    implements ResourceWithNumberOfInstances {

  private long numberOfInstances;

  public FolderServerTemplateCurrentUserReport() {
    super(CedarResourceType.TEMPLATE);
  }

  @Override
  public long getNumberOfInstances() {
    return numberOfInstances;
  }

  @Override
  public void setNumberOfInstances(long numberOfInstances) {
    this.numberOfInstances = numberOfInstances;
  }
}
