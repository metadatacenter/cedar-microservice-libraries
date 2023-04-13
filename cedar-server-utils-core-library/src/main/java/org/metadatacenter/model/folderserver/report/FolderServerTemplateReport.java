package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithNumberOfInstances;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerTemplateReport extends FolderServerSchemaArtifactReport implements ResourceWithNumberOfInstances {

  private long numberOfInstances;

  public FolderServerTemplateReport() {
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
