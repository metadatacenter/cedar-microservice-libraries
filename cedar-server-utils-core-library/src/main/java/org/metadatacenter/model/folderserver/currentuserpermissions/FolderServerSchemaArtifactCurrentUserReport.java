package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.datagroup.*;

public class FolderServerSchemaArtifactCurrentUserReport extends FolderServerArtifactCurrentUserReport implements ResourceWithVersionData,
    ResourceWithPreviousVersionData, ResourceWithOpenFlag {

  protected VersionDataGroup versionDataGroup;
  protected PreviousVersionGroup previousVersionGroup;

  public FolderServerSchemaArtifactCurrentUserReport(CedarResourceType resourceType) {
    super(resourceType);
    versionDataGroup = new VersionDataGroup();
    previousVersionGroup = new PreviousVersionGroup();
  }

  @Override
  public ResourceVersion getVersion() {
    return versionDataGroup.getVersion();
  }

  @Override
  public void setVersion(String versionString) {
    versionDataGroup.setVersion(ResourceVersion.forValue(versionString));
  }

  @Override
  public Boolean isLatestVersion() {
    return versionDataGroup.isLatestVersion();
  }

  @Override
  public void setLatestVersion(Boolean latestVersion) {
    versionDataGroup.setLatestVersion(latestVersion);
  }

  @Override
  public Boolean isLatestDraftVersion() {
    return versionDataGroup.isLatestDraftVersion();
  }

  @Override
  public void setLatestDraftVersion(Boolean latestDraftVersion) {
    versionDataGroup.setLatestDraftVersion(latestDraftVersion);
  }

  @Override
  public Boolean isLatestPublishedVersion() {
    return versionDataGroup.isLatestPublishedVersion();
  }

  @Override
  public void setLatestPublishedVersion(Boolean latestPublishedVersion) {
    versionDataGroup.setLatestPublishedVersion(latestPublishedVersion);
  }

  @Override
  public BiboStatus getPublicationStatus() {
    return versionDataGroup.getPublicationStatus();
  }

  @Override
  public void setPublicationStatus(String s) {
    versionDataGroup.setPublicationStatus(BiboStatus.forValue(s));
  }

  @Override
  public CedarUntypedSchemaArtifactId getPreviousVersion() {
    return previousVersionGroup.getPreviousVersion();
  }

  @Override
  public void setPreviousVersion(CedarUntypedSchemaArtifactId pv) {
    previousVersionGroup.setPreviousVersion(pv);
  }

  @JsonIgnore
  public boolean hasPreviousVersion() {
    return getPreviousVersion() != null && getPreviousVersion().getId() != null;
  }

}
