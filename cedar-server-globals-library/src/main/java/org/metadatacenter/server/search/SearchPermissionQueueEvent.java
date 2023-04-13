package org.metadatacenter.server.search;

import org.metadatacenter.constant.CedarConstants;

import java.time.Instant;

public class SearchPermissionQueueEvent {

  private String id;
  private SearchPermissionQueueEventType eventType;
  private String createdAt;
  private long createdAtTS;

  public SearchPermissionQueueEvent() {
  }

  public SearchPermissionQueueEvent(String id, SearchPermissionQueueEventType eventType) {
    this.id = id;
    this.eventType = eventType;
    Instant now = Instant.now();
    this.createdAt = CedarConstants.xsdDateTimeFormatter.format(now);
    this.createdAtTS = now.getEpochSecond();
  }

  public String getId() {
    return id;
  }

  public SearchPermissionQueueEventType getEventType() {
    return eventType;
  }

  public String getCreatedAt() {
    return createdAt;
  }

  public long getCreatedAtTS() {
    return createdAtTS;
  }

}
