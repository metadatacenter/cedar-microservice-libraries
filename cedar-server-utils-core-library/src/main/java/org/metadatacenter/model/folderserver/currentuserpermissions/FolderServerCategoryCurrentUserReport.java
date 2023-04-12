package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryWithCurrentUserPermissions;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerCategoryCurrentUserReport extends FolderServerCategory implements CategoryWithCurrentUserPermissions {

  private CurrentUserCategoryPermissions currentUserPermissions = new CurrentUserCategoryPermissions();
  private boolean root;
  private static final Logger log = LoggerFactory.getLogger(FolderServerCategoryCurrentUserReport.class);


  public FolderServerCategoryCurrentUserReport() {
    super();
  }

  public static FolderServerCategoryCurrentUserReport fromCategory(FolderServerCategory category) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(category);
      return JsonMapper.MAPPER.readValue(s, FolderServerCategoryCurrentUserReport.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.OnTheFly.CURRENT_USER_PERMISSIONS)
  public CurrentUserCategoryPermissions getCurrentUserPermissions() {
    return currentUserPermissions;
  }

  @Override
  public boolean isRoot() {
    return root;
  }

  public void setRoot(boolean root) {
    this.root = root;
  }

}
