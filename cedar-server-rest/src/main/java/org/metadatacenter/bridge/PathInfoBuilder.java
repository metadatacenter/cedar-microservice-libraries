package org.metadatacenter.bridge;

import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.PermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CedarPermission;

import java.util.List;

public final class PathInfoBuilder {

  private PathInfoBuilder() {
  }

  public static List<FolderServerResourceExtract> getNodePathExtract(CedarRequestContext context,
                                                                     FolderServiceSession folderSession,
                                                                     PermissionServiceSession permissionSession,
                                                                     FileSystemResource node) {
    List<FolderServerResourceExtract> pathInfo = folderSession.findNodePathExtract(node);
    for (FolderServerResourceExtract extract : pathInfo) {
      extract.setActiveUserCanRead(activeUserCanRead(context, permissionSession, extract));
    }
    return pathInfo;
  }

  private static boolean activeUserCanRead(CedarRequestContext context, PermissionServiceSession permissionSession,
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
    return permissionSession.userHasReadAccessToNode(nodeExtract.getId());
  }
}
