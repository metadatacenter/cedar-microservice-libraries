package org.metadatacenter.server.resource;

import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.CedarTemplateId;

import java.time.Instant;

public class CloneInstancesQueueEvent {

  private String oldId;
  private String newId;
  private String newFolderName;
  private String createdAt;
  private long createdAtTS;

  public CloneInstancesQueueEvent() {
  }

  public CloneInstancesQueueEvent(CedarTemplateId oldId, CedarTemplateId newId, String newFolderName) {
    this.oldId = oldId.getId();
    this.newId = newId.getId();
    this.newFolderName = newFolderName;
    Instant now = Instant.now();
    this.createdAt = CedarConstants.xsdDateTimeFormatter.format(now);
    this.createdAtTS = now.getEpochSecond();
  }

  public String getOldId() {
    return oldId;
  }

  public String getNewId() {
    return newId;
  }

  public String getNewFolderName() {
    return newFolderName;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public long getCreatedAtTS() {
    return createdAtTS;
  }

}
