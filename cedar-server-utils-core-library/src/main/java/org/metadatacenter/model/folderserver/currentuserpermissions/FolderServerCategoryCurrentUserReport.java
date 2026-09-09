package org.metadatacenter.model.folderserver.currentuserpermissions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.CurrentUserCategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryWithCurrentUserPermissions;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class FolderServerCategoryCurrentUserReport extends FolderServerCategory implements CategoryWithCurrentUserPermissions {

  private CurrentUserCategoryPermissions currentUserPermissions = new CurrentUserCategoryPermissions();
  private boolean root;
  private static final Logger log = LoggerFactory.getLogger(FolderServerCategoryCurrentUserReport.class);


  public FolderServerCategoryCurrentUserReport() {
    super();
  }

  public static FolderServerCategoryCurrentUserReport fromCategory(FolderServerCategory category) {
    try {
      String s = JsonMapper.STRICT_MAPPER.writeValueAsString(category);
      FolderServerCategoryCurrentUserReport report =
          JsonMapper.TOLERANT_MAPPER.readValue(s, FolderServerCategoryCurrentUserReport.class);
      report.setRoot(category.getParentCategoryId() == null);
      return report;
    } catch (IOException e) {
      log.error("Error while converting the category to a current-user report", e);
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
