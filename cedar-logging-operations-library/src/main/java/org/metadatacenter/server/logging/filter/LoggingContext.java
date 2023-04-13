package org.metadatacenter.server.logging.filter;

public class LoggingContext {
  private String globalRequestId;

  private String localRequestId;

  public LoggingContext(String globalRequestId, String localRequestId) {
    this.globalRequestId = globalRequestId;
    this.localRequestId = localRequestId;
  }

  public String getGlobalRequestId() {
    return globalRequestId;
  }

  public String getLocalRequestId() {
    return localRequestId;
  }
}
