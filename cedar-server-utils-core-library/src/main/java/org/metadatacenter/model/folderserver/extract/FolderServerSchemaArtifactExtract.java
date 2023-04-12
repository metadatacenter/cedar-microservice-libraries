package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithVersionData;
import org.metadatacenter.model.folderserver.datagroup.VersionDataGroup;
import org.metadatacenter.server.security.model.NodeWithPublicationStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerSchemaArtifactExtract extends FolderServerArtifactExtract implements NodeWithPublicationStatus, ResourceWithVersionData {

  protected VersionDataGroup versionData;

  public FolderServerSchemaArtifactExtract(CedarResourceType resourceType) {
    super(resourceType);
    versionData = new VersionDataGroup();
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

  @Override
  @JsonIgnore
  public CedarSchemaArtifactId getResourceId() {
    return CedarSchemaArtifactId.build(this.getId(), this.getType());
  }
}
