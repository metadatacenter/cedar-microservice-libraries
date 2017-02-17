package org.metadatacenter.server.search.elasticsearch.worker;

import org.elasticsearch.action.search.SearchResponse;

public class SearchResponseResult {

  private SearchResponse response;
  private NodeIdResultList resultList;

  public SearchResponseResult(SearchResponse response, NodeIdResultList resultList) {
    this.response = response;
    this.resultList = resultList;
  }

  public SearchResponse getResponse() {
    return response;
  }

  public NodeIdResultList getResultList() {
    return resultList;
  }
}
