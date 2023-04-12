package org.metadatacenter.model.response;

import org.metadatacenter.model.request.NodeListRequest;

import java.util.Map;

public class AbstractNodeListResponse {

  protected NodeListRequest request;
  protected long totalCount;
  protected long currentOffset;
  protected Map<String, String> paging;

  public NodeListRequest getRequest() {
    return request;
  }

  public void setRequest(NodeListRequest request) {
    this.request = request;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public void setTotalCount(long totalCount) {
    this.totalCount = totalCount;
  }

  public long getCurrentOffset() {
    return currentOffset;
  }

  public void setCurrentOffset(long currentOffset) {
    this.currentOffset = currentOffset;
  }

  public Map<String, String> getPaging() {
    return paging;
  }

  public void setPaging(Map<String, String> paging) {
    this.paging = paging;
  }

}
