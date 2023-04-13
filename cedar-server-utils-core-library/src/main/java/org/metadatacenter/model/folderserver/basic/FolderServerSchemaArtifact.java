package org.metadatacenter.model.folderserver.basic;

import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.datagroup.PreviousVersionGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithPreviousVersionData;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithVersionData;
import org.metadatacenter.model.folderserver.datagroup.VersionDataGroup;
import org.metadatacenter.server.security.model.NodeWithPublicationStatus;

public abstract class FolderServerSchemaArtifact extends FolderServerArtifact implements ResourceWithPreviousVersionData, NodeWithPublicationStatus
    , ResourceWithVersionData {

  protected VersionDataGroup versionData;
  protected PreviousVersionGroup previousVersionGroup;

  public FolderServerSchemaArtifact(CedarResourceType resourceType) {
    super(resourceType);
    versionData = new VersionDataGroup();
    previousVersionGroup = new PreviousVersionGroup();
  }

  @Override
  public CedarUntypedSchemaArtifactId getPreviousVersion() {
    return previousVersionGroup.getPreviousVersion();
  }

  @Override
  public void setPreviousVersion(CedarUntypedSchemaArtifactId pv) {
    previousVersionGroup.setPreviousVersion(pv);
  }

  @Override
  public ResourceVersion getVersion() {
    return versionData.getVersion();
  }

  @Override
  public void setVersion(String versionString) {
    versionData.setVersion(ResourceVersion.forValue(versionString));
  }

  @Override
  public Boolean isLatestVersion() {
    return versionData.isLatestVersion();
  }

  @Override
  public void setLatestVersion(Boolean latestVersion) {
    versionData.setLatestVersion(latestVersion);
  }

  @Override
  public Boolean isLatestDraftVersion() {
    return versionData.isLatestDraftVersion();
  }

  @Override
  public void setLatestDraftVersion(Boolean latestDraftVersion) {
    versionData.setLatestDraftVersion(latestDraftVersion);
  }

  @Override
  public Boolean isLatestPublishedVersion() {
    return versionData.isLatestPublishedVersion();
  }

  @Override
  public void setLatestPublishedVersion(Boolean latestPublishedVersion) {
    versionData.setLatestPublishedVersion(latestPublishedVersion);
  }

  @Override
  public BiboStatus getPublicationStatus() {
    return versionData.getPublicationStatus();
  }

  @Override
  public void setPublicationStatus(String s) {
    versionData.setPublicationStatus(BiboStatus.forValue(s));
  }

}
