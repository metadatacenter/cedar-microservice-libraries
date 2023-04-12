package org.metadatacenter.server.valuerecommender.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.id.CedarInstanceArtifactId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ValuerecommenderReindexMessage {

  private CedarTemplateId templateId;
  private CedarTemplateInstanceId instanceId;
  private ValuerecommenderReindexMessageResourceType resourceType;
  private ValuerecommenderReindexMessageActionType actionType;
  private Instant creationTime;

  public ValuerecommenderReindexMessage() {
  }

  public ValuerecommenderReindexMessage(CedarTemplateId templateId, CedarTemplateInstanceId instanceId,
                                        ValuerecommenderReindexMessageResourceType resourceType,
                                        ValuerecommenderReindexMessageActionType actionType) {
    this.templateId = templateId;
    this.instanceId = instanceId;
    this.resourceType = resourceType;
    this.actionType = actionType;
    this.creationTime = Instant.now();
  }

  public CedarTemplateId getTemplateId() {
    return templateId;
  }

  public CedarInstanceArtifactId getInstanceId() {
    return instanceId;
  }

  public ValuerecommenderReindexMessageResourceType getResourceType() {
    return resourceType;
  }

  public ValuerecommenderReindexMessageActionType getActionType() {
    return actionType;
  }

  public Instant getCreationTime() {
    return creationTime;
  }
}
