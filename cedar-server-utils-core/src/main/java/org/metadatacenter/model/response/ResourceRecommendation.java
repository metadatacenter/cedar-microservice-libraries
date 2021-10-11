package org.metadatacenter.model.response;

import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;

public class ResourceRecommendation implements Comparable<ResourceRecommendation> {

  double recommendationScore;
  int sourceFieldsMatched;
  int targetFieldsCount;
  FolderServerResourceExtract resourceExtract;

  public ResourceRecommendation() { }

  public ResourceRecommendation(double recommendationScore, int sourceFieldsMatched, int targetFieldsCount,
                                FolderServerResourceExtract resourceExtract) {
    this.recommendationScore = recommendationScore;
    this.sourceFieldsMatched = sourceFieldsMatched;
    this.targetFieldsCount = targetFieldsCount;
    this.resourceExtract = resourceExtract;
  }

  public double getRecommendationScore() {
    return recommendationScore;
  }

  public void setRecommendationScore(double recommendationScore) {
    this.recommendationScore = recommendationScore;
  }

  public int getSourceFieldsMatched() {
    return sourceFieldsMatched;
  }

  public void setSourceFieldsMatched(int sourceFieldsMatched) {
    this.sourceFieldsMatched = sourceFieldsMatched;
  }

  public int getTargetFieldsCount() {
    return targetFieldsCount;
  }

  public void setTargetFieldsCount(int targetFieldsCount) {
    this.targetFieldsCount = targetFieldsCount;
  }

  public FolderServerResourceExtract getResourceExtract() {
    return resourceExtract;
  }

  public void setResourceExtract(FolderServerResourceExtract resourceExtract) {
    this.resourceExtract = resourceExtract;
  }

  // Used to rank the results
  @Override
  public int compareTo(ResourceRecommendation r) {
    return Double.compare(r.getRecommendationScore(), this.getRecommendationScore());
  }

}
