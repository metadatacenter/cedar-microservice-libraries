package org.metadatacenter.bridge;

import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;

import java.util.List;

public final class PathInfoBuilder {

  private PathInfoBuilder() {
  }

  /**
   * The path from the root to this resource, and nothing more.
   *
   * <p>{@link #getResourcePathExtract} decorates every element of the path with what the current user
   * may do with it, which costs several graph queries per element. A caller that needs the shape of
   * the path — its ids, names and types — rather than one user's authority over it should ask for it
   * here and not pay for the decoration.
   *
   * <p>Indexing is such a caller, and was the expensive one: it built the decorated path for every
   * resource in the repository, and {@code FolderServerNodeInfo.fromNode} then read a single
   * {@code getId()} off it to learn the parent folder. The permissions, the capabilities and the
   * implicit-open flag were all computed and dropped, for the one user who happened to be running
   * the rebuild.
   */
  public static List<FolderServerResourceExtract> getResourcePath(FolderServiceSession folderSession,
                                                                  FileSystemResource node) {
    return folderSession.findNodePathExtract(node);
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
      addCurrentUserPermissions(permissionSession, extract);
      extract.setActiveUserCanRead(activeUserCanRead(context, permissionSession, extract));
    }
    return pathInfo;
  }

  /** Adds the canonical role, ownership and capability projection to one resource extract. */
  public static void addCurrentUserPermissions(ResourcePermissionServiceSession permissionSession,
                                               FolderServerResourceExtract extract) {
    CurrentUserResourcePermissions permissions = new CurrentUserResourcePermissions();
    permissions.applyAuthority(permissionSession.getResourceAuthority(extract.getResourceId()), extract.getType());
    extract.setCurrentUserPermissions(permissions);
  }

  private static boolean activeUserCanRead(CedarRequestContext context, ResourcePermissionServiceSession permissionSession,
                                           FolderServerResourceExtract nodeExtract) {
    if (context.getCedarUser().has(CedarPermission.READ_NOT_READABLE_NODE)) {
      return true;
    }
    if (nodeExtract instanceof FolderServerFolderExtract folderExtract) {
      if (folderExtract.isRoot() || folderExtract.isSystem()) {
        return false;
      }
    }
    return permissionSession.userHasCapability(nodeExtract.getResourceId(), ResourceCapability.READ_RESOURCE);
  }
}
