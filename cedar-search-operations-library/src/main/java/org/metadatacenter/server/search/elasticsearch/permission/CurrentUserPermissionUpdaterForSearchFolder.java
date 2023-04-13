package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.user.CedarUser;

public class CurrentUserPermissionUpdaterForSearchFolder extends AbstractCurrentUserPermissionUpdaterForSearch {

  private CurrentUserPermissionUpdaterForSearchFolder(IndexedDocumentDocument indexedDocument, CedarUser cedarUser,
                                                      CedarConfig cedarConfig) {
    super(indexedDocument, cedarUser, cedarConfig);
  }

  public static CurrentUserPermissionUpdater get(IndexedDocumentDocument indexedDocument, CedarUser cedarUser,
                                                 CedarConfig cedarConfig) {
    return new CurrentUserPermissionUpdaterForSearchFolder(indexedDocument, cedarUser, cedarConfig);
  }

  @Override
  public void update(CurrentUserResourcePermissions currentUserResourcePermissions) {
    if (userCanWrite()) {
      currentUserResourcePermissions.setCanWrite(true);
      currentUserResourcePermissions.setCanDelete(true);
      currentUserResourcePermissions.setCanRead(true);

      FolderServerNodeInfo info = indexedDocument.getInfo();
      if (!info.getIsRoot() && !info.getIsSystem() && !info.getIsUserHome()) {
        currentUserResourcePermissions.setCanShare(true);
      }
    } else if (userCanRead()) {
      currentUserResourcePermissions.setCanRead(true);
    }
    if (userCanChangeOwnerOfFolder()) {
      currentUserResourcePermissions.setCanChangeOwner(true);
    }
  }

}
