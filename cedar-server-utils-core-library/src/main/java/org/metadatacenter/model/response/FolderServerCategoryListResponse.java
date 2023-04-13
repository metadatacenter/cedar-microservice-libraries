package org.metadatacenter.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.request.CategoryListRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerCategoryListResponse {

  protected CategoryListRequest request;
  private long totalCount;
  private long currentOffset;
  private Map<String, String> paging;
  private List<FolderServerCategory> categories;

  public CategoryListRequest getRequest() {
    return request;
  }

  public void setRequest(CategoryListRequest request) {
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

  public FolderServerCategoryListResponse() {
    this.categories = new ArrayList<>();
  }

  public List<FolderServerCategory> getCategories() {
    return categories;
  }

  public void setCategories(List<FolderServerCategory> categories) {
    this.categories = categories;
  }
}
