package org.metadatacenter.bridge;

import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CedarPermission;

import java.util.List;

public final class PathInfoBuilder {

  private PathInfoBuilder() {
  }

  public static List<FolderServerResourceExtract> getResourcePathExtract(CedarRequestContext context,
                                                                         FolderServiceSession folderSession,
                                                                         ResourcePermissionServiceSession permissionSession,
                                                                         FileSystemResource node) {
    List<FolderServerResourceExtract> pathInfo = folderSession.findNodePathExtract(node);
    boolean isOpenImplicitly = false;
    for (FolderServerResourceExtract extract : pathInfo) {
      if (extract.getIsOpen() != null && extract.getIsOpen()) {
        isOpenImplicitly = true;
      }
      extract.setIsOpenImplicitly(isOpenImplicitly);
      extract.setActiveUserCanRead(activeUserCanRead(context, permissionSession, extract));
    }
    return pathInfo;
  }

  private static boolean activeUserCanRead(CedarRequestContext context, ResourcePermissionServiceSession permissionSession,
                                           FolderServerResourceExtract nodeExtract) {
    if (context.getCedarUser().has(CedarPermission.READ_NOT_READABLE_NODE)) {
      return true;
    }
    if (nodeExtract instanceof FolderServerFolderExtract) {
      FolderServerFolderExtract folderExtract = (FolderServerFolderExtract) nodeExtract;
      if (folderExtract.isRoot() || folderExtract.isSystem()) {
        return false;
      }
    }
    return permissionSession.userHasReadAccessToResource(nodeExtract.getResourceId());
  }
}
