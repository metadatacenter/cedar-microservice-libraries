package org.metadatacenter.model.folderserver.datagroup;

public class UserNamesDataGroup {

  private String createdByUserName;
  private String lastUpdatedByUserName;
  private String ownedByUserName;

  public UserNamesDataGroup() {
  }

  public String getCreatedByUserName() {
    return createdByUserName;
  }

  public void setCreatedByUserName(String createdByUserName) {
    this.createdByUserName = createdByUserName;
  }

  public String getLastUpdatedByUserName() {
    return lastUpdatedByUserName;
  }

  public void setLastUpdatedByUserName(String lastUpdatedByUserName) {
    this.lastUpdatedByUserName = lastUpdatedByUserName;
  }

  public String getOwnedByUserName() {
    return ownedByUserName;
  }

  public void setOwnedByUserName(String ownedByUserName) {
    this.ownedByUserName = ownedByUserName;
  }
}
