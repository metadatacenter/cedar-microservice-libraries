package org.metadatacenter.model.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.model.folderserver.extract.FolderServerUserExtract;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerUserListResponse {

  private List<FolderServerUserExtract> users;

  public FolderServerUserListResponse() {
    this.users = new ArrayList<>();
  }

  public List<FolderServerUserExtract> getUsers() {
    return users;
  }

  public void setUsers(List<FolderServerUser> users) {
    this.users = new ArrayList<>();
    for (FolderServerUser user : users) {
      this.users.add(FolderServerUserExtract.fromFolderServerUser(user));
    }
  }
}
