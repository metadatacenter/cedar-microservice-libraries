package org.metadatacenter.model.folderserver.datagroup;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.model.provenance.ProvenanceTime;

public class BaseDataGroup {

  protected String id;
  protected CedarResourceType resourceType;
  protected ProvenanceTime createdOn;
  protected ProvenanceTime lastUpdatedOn;
  protected String sourceHash;

  public BaseDataGroup() {
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public CedarResourceType getResourceType() {
    return resourceType;
  }

  public void setResourceType(CedarResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public ProvenanceTime getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(ProvenanceTime createdOn) {
    this.createdOn = createdOn;
  }

  public ProvenanceTime getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public void setLastUpdatedOn(ProvenanceTime lastUpdatedOn) {
    this.lastUpdatedOn = lastUpdatedOn;
  }

  public String getSourceHash() {
    return sourceHash;
  }

  public void setSourceHash(String sourceHash) {
    this.sourceHash = sourceHash;
  }
}
