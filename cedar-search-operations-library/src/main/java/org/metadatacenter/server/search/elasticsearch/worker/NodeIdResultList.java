package org.metadatacenter.server.search.elasticsearch.worker;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class NodeIdResultList {

  private final Map<String, String> elasticToCedar;
  private long totalCount;

  public NodeIdResultList() {
    elasticToCedar = new LinkedHashMap<>();
  }

  public void addId(String elasticId, String cedarId) {
    elasticToCedar.put(elasticId, cedarId);
  }

  public Set<String> getElasticIds() {
    return elasticToCedar.keySet();
  }

  public Collection<String> getCedarIds() {
    return elasticToCedar.values();
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public int getCount() {
    return elasticToCedar.size();
  }

}
