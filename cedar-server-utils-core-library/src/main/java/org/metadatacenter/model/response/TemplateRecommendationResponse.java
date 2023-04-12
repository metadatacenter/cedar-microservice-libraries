package org.metadatacenter.model.response;

import org.metadatacenter.model.request.TemplateRecommendationRequestSummary;

import java.util.List;

public class TemplateRecommendationResponse {

  private int totalCount;
  private TemplateRecommendationRequestSummary requestSummary;
  List<ResourceRecommendation> recommendations;

  public TemplateRecommendationResponse() { }

  public int getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(int totalCount) {
    this.totalCount = totalCount;
  }

  public TemplateRecommendationRequestSummary getRequestSummary() {
    return requestSummary;
  }

  public void setRequestSummary(TemplateRecommendationRequestSummary requestSummary) {
    this.requestSummary = requestSummary;
  }

  public List<ResourceRecommendation> getRecommendations() {
    return recommendations;
  }

  public void setRecommendations(List<ResourceRecommendation> recommendations) {
    this.recommendations = recommendations;
  }
}
