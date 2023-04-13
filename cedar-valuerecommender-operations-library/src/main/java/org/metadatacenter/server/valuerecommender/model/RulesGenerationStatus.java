package org.metadatacenter.server.valuerecommender.model;

import java.time.Duration;
import java.time.Instant;

public class RulesGenerationStatus {

  // Status enum
  public enum Status {
    PROCESSING, COMPLETED
  }

  // Attributes
  private String templateId;
  private int templateInstancesCount;
  private Instant startTime;
  private Instant finishTime;
  private Duration executionDuration;
  private Integer rulesIndexedCount;
  private Status status;

  public RulesGenerationStatus() {
  }

  public RulesGenerationStatus(String templateId, int templateInstancesCount, Instant startTime,
                               Instant finishTime, Duration executionDuration, Integer rulesIndexedCount, Status status) {
    this.templateId = templateId;
    this.templateInstancesCount = templateInstancesCount;
    this.startTime = startTime;
    this.finishTime = finishTime;
    this.executionDuration = executionDuration;
    this.rulesIndexedCount = rulesIndexedCount;
    this.status = status;
  }

  public RulesGenerationStatus(String templateId, int templateInstancesCount, Instant startTime, Status status) {
    this(templateId, templateInstancesCount, startTime, null, null, null, status);
  }

  public String getTemplateId() {
    return templateId;
  }

  public int getTemplateInstancesCount() {
    return templateInstancesCount;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getFinishTime() {
    return finishTime;
  }

  public Duration getExecutionDuration() {
    return executionDuration;
  }

  public Integer getRulesIndexedCount() {
    return rulesIndexedCount;
  }

  public Status getStatus() {
    return status;
  }

  public void setFinishTime(Instant finishTime) {
    this.finishTime = finishTime;
  }

  public void setExecutionDuration(Duration executionDuration) {
    this.executionDuration = executionDuration;
  }

  public void setRulesIndexedCount(Integer rulesIndexedCount) {
    this.rulesIndexedCount = rulesIndexedCount;
  }

  public void setStatus(Status status) {
    this.status = status;
  }
}
