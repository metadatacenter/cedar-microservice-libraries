package org.metadatacenter.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.util.FolderServerNodeContext;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerNodeListResponse extends AbstractNodeListResponse {

  private List<? extends FolderServerResourceExtract> resources;
  private List<? extends FolderServerResourceExtract> pathInfo;
  private NodeListQueryType nodeListQueryType;
  private String categoryName;
  private List<FolderServerCategoryExtract> categoryPath;
  private String continuation;

  public List<? extends FolderServerResourceExtract> getResources() {
    return resources;
  }

  public void setResources(List<? extends FolderServerResourceExtract> resources) {
    this.resources = resources;
  }

  public List<? extends FolderServerResourceExtract> getPathInfo() {
    return pathInfo;
  }

  public void setPathInfo(List<FolderServerResourceExtract> pathInfo) {
    this.pathInfo = pathInfo;
  }

  public NodeListQueryType getNodeListQueryType() {
    return nodeListQueryType;
  }

  public void setNodeListQueryType(NodeListQueryType nodeListQueryType) {
    this.nodeListQueryType = nodeListQueryType;
  }

  public String getCategoryName() {
    return categoryName;
  }

  public void setCategoryName(String categoryName) {
    this.categoryName = categoryName;
  }

  public void setCategoryPath(List<FolderServerCategoryExtract> categoryPath) {
    this.categoryPath = categoryPath;
  }

  public List<FolderServerCategoryExtract> getCategoryPath() {
    return categoryPath;
  }

  /**
   * Where the next page of this search starts, for a caller paging with a continuation rather than an
   * offset. Absent on the last page, and absent from every offset-paged answer.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getContinuation() {
    return continuation;
  }

  public void setContinuation(String continuation) {
    this.continuation = continuation;
  }

  @JsonProperty("@context")
  public Map<String, String> getContext() {
    return FolderServerNodeContext.getContext();
  }
}
