package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerResourceCurrentUserReport;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerFolderReport.class, name = CedarResourceType.Types.FOLDER),
})
public abstract class FolderServerResourceReport extends FolderServerResourceCurrentUserReport {

  public FolderServerResourceReport(CedarResourceType resourceType) {
    super(resourceType);
  }

}
