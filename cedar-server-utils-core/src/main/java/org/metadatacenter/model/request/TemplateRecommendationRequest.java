package org.metadatacenter.model.request;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class TemplateRecommendationRequest {

  @Valid
  @NotNull
  private JsonNode metadataRecord;

  public TemplateRecommendationRequest() { }

  public JsonNode getMetadataRecord() {
    return metadataRecord;
  }

  public void setMetadataRecord(JsonNode metadataRecord) {
    this.metadataRecord = metadataRecord;
  }

}
