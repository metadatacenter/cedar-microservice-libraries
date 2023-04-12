package org.metadatacenter.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.util.FolderServerNodeContext;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerNodeMapListResponse extends AbstractNodeListResponse {

  private List<Map<String, Object>> resources;
  private List<? extends FolderServerResourceExtract> pathInfo;
  private NodeListQueryType nodeListQueryType;
  private String categoryName;
  private List<FolderServerCategoryExtract> categoryPath;

  public List<Map<String, Object>> getResources() {
    return resources;
  }

  public void setResources(List<Map<String, Object>> resources) {
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

  @JsonProperty("@context")
  public Map<String, String> getContext() {
    return FolderServerNodeContext.getContext();
  }
}
