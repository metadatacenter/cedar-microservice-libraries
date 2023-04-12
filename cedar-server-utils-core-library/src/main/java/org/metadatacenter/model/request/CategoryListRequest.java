package org.metadatacenter.model.request;

public class CategoryListRequest {

  private long limit;
  private long offset;

  public long getLimit() {
    return limit;
  }

  public void setLimit(long limit) {
    this.limit = limit;
  }

  public long getOffset() {
    return offset;
  }

  public void setOffset(long offset) {
    this.offset = offset;
  }

}
