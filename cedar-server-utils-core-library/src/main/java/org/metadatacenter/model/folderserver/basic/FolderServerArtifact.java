package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerArtifactCurrentUserReport;
import org.metadatacenter.model.folderserver.datagroup.DOIGroup;
import org.metadatacenter.model.folderserver.datagroup.DerivedFromGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithDOIData;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithDerivedFromData;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerField.class, name = CedarResourceType.Types.FIELD),
    @JsonSubTypes.Type(value = FolderServerElement.class, name = CedarResourceType.Types.ELEMENT),
    @JsonSubTypes.Type(value = FolderServerTemplate.class, name = CedarResourceType.Types.TEMPLATE),
    @JsonSubTypes.Type(value = FolderServerInstance.class, name = CedarResourceType.Types.INSTANCE)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FolderServerArtifact extends FileSystemResource implements ResourceWithDerivedFromData, ResourceWithDOIData {

  private static final Logger log = LoggerFactory.getLogger(FolderServerArtifact.class);

  protected DerivedFromGroup provenanceDataGroup;
  protected DOIGroup doiDataGroup;

  public FolderServerArtifact(CedarResourceType resourceType) {
    super(resourceType);
    provenanceDataGroup = new DerivedFromGroup();
    doiDataGroup = new DOIGroup();
  }

  public static FolderServerArtifact fromFolderServerResourceCurrentUserReport(FolderServerArtifactCurrentUserReport cur) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(cur);
      FolderServerArtifact folderServerArtifact = JsonMapper.MAPPER.readValue(s, FolderServerArtifact.class);
      return folderServerArtifact;
    } catch (IOException e) {
      log.error("Error while converting the current-user report to a FolderServerArtifact", e);
    }
    return null;
  }

  @Override
  public CedarUntypedArtifactId getDerivedFrom() {
    return provenanceDataGroup.getDerivedFrom();
  }

  @Override
  public void setDerivedFrom(CedarUntypedArtifactId df) {
    provenanceDataGroup.setDerivedFrom(df);
  }

  @Override
  public String getDOI() {
    return doiDataGroup.getDOI();
  }

  @Override
  public void setDOI(String doi) {
    doiDataGroup.setDOI(doi);
  }
}
