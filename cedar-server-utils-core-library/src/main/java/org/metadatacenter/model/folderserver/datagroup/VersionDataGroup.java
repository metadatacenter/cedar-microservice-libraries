package org.metadatacenter.model.folderserver.datagroup;

import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.ResourceVersion;

public class VersionDataGroup {

  private ResourceVersion version;
  private Boolean latestVersion;
  private Boolean latestDraftVersion;
  private Boolean latestPublishedVersion;
  private BiboStatus publicationStatus;

  public VersionDataGroup() {
  }

  public ResourceVersion getVersion() {
    return version;
  }

  public void setVersion(ResourceVersion version) {
    this.version = version;
  }

  public Boolean isLatestVersion() {
    return latestVersion;
  }

  public void setLatestVersion(Boolean latestVersion) {
    this.latestVersion = latestVersion;
  }

  public Boolean isLatestDraftVersion() {
    return latestDraftVersion;
  }

  public void setLatestDraftVersion(Boolean latestDraftVersion) {
    this.latestDraftVersion = latestDraftVersion;
  }

  public Boolean isLatestPublishedVersion() {
    return latestPublishedVersion;
  }

  public void setLatestPublishedVersion(Boolean latestPublishedVersion) {
    this.latestPublishedVersion = latestPublishedVersion;
  }

  public BiboStatus getPublicationStatus() {
    return publicationStatus;
  }

  public void setPublicationStatus(BiboStatus publicationStatus) {
    this.publicationStatus = publicationStatus;
  }
  
}
