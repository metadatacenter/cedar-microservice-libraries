package org.metadatacenter.model.folderserver.currentuserpermissions;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithNumberOfInstances;

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
