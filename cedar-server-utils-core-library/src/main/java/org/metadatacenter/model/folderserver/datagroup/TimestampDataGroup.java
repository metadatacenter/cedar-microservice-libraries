package org.metadatacenter.model.folderserver.datagroup;

public class TimestampDataGroup {

  protected long createdOnTS;
  protected long lastUpdatedOnTS;

  public TimestampDataGroup() {
  }

  public long getCreatedOnTS() {
    return createdOnTS;
  }

  public void setCreatedOnTS(long createdOnTS) {
    this.createdOnTS = createdOnTS;
  }

  public long getLastUpdatedOnTS() {
    return lastUpdatedOnTS;
  }

  public void setLastUpdatedOnTS(long lastUpdatedOnTS) {
    this.lastUpdatedOnTS = lastUpdatedOnTS;
  }
}
