package org.metadatacenter.model.response;

import org.metadatacenter.model.request.TemplateRecommendationRequestSummary;

import java.util.List;

public class TemplateRecommendationResponse {

  private long totalCount;
  private TemplateRecommendationRequestSummary request;
  List<ResourceRecommendation> recommendations;

  public TemplateRecommendationResponse() { }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public TemplateRecommendationRequestSummary getRequest() {
    return request;
  }

  public void setRequest(TemplateRecommendationRequestSummary request) {
    this.request = request;
  }

  public List<ResourceRecommendation> getRecommendations() {
    return recommendations;
  }

  public void setRecommendations(List<ResourceRecommendation> recommendations) {
    this.recommendations = recommendations;
  }
}
