package org.metadatacenter.server.model.provenance;

import java.lang.String;

public class ProvenanceInfo {
  private String createdOn;
  private String createdBy;
  private String lastUpdatedOn;
  private String lastUpdatedBy;

  public ProvenanceInfo() {
  }

  public String getCreatedOn() {
    return createdOn;
  }

  public void setCreatedOn(String createdOn) {
    this.createdOn = createdOn;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public void setLastUpdatedOn(String lastUpdatedOn) {
    this.lastUpdatedOn = lastUpdatedOn;
  }

  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }
}





