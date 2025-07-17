package org.metadatacenter.server.resource;

import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.CedarTemplateId;

import java.time.Instant;

public class CloneInstancesQueueEvent {

  private String oldId;
  private String newId;
  private String createdAt;
  private long createdAtTS;

  public CloneInstancesQueueEvent() {
  }

  public CloneInstancesQueueEvent(CedarTemplateId oldId, CedarTemplateId newId) {
    this.oldId = oldId.getId();
    this.newId = newId.getId();
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

  public String getCreatedAt() {
    return createdAt;
  }

  public long getCreatedAtTS() {
    return createdAtTS;
  }

}
