package org.metadatacenter.server.search.elasticsearch.worker;

import org.elasticsearch.search.SearchHit;

import java.util.ArrayList;
import java.util.List;

public class SearchResponseResult {

  private final List<SearchHit> hits;
  private long totalCount;

  public SearchResponseResult() {
    this.hits = new ArrayList<>();
  }

  public List<SearchHit> getHits() {
    return hits;
  }

  public void add(SearchHit hit) {
    hits.add(hit);
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public long getTotalCount() {
    return totalCount;
  }
}
