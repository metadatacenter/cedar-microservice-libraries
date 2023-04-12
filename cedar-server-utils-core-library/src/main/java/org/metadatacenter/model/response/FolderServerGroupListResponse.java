package org.metadatacenter.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerGroupListResponse {

  private List<FolderServerGroup> groups;

  public FolderServerGroupListResponse() {
    this.groups = new ArrayList<>();
  }

  public List<FolderServerGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<FolderServerGroup> groups) {
    this.groups = groups;
  }
}
