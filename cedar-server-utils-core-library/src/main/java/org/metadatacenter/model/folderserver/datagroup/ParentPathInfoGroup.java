package org.metadatacenter.model.folderserver.datagroup;

import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;

import java.util.ArrayList;
import java.util.List;

public class ParentPathInfoGroup {

  protected String path;
  protected String parentPath;
  private List<FolderServerResourceExtract> pathInfo;

  public ParentPathInfoGroup() {
    this.pathInfo = new ArrayList<>();
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getParentPath() {
    return parentPath;
  }

  public void setParentPath(String parentPath) {
    this.parentPath = parentPath;
  }

  public List<FolderServerResourceExtract> getPathInfo() {
    return pathInfo;
  }

  public void setPathInfo(List<FolderServerResourceExtract> pathInfo) {
    this.pathInfo = pathInfo;
  }
}
