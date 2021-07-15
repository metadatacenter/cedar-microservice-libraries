package org.metadatacenter.model.request;

public class TemplateRecommendationRequestSummary {

  private int sourceFieldsCount;

  public TemplateRecommendationRequestSummary() { }

  public TemplateRecommendationRequestSummary(int sourceFieldsCount) {
    this.sourceFieldsCount = sourceFieldsCount;
  }

  public int getSourceFieldsCount() {
    return sourceFieldsCount;
  }

  public void setSourceFieldsCount(int sourceFieldsCount) {
    this.sourceFieldsCount = sourceFieldsCount;
  }
}
