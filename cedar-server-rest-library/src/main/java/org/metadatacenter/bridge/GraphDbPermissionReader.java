package org.metadatacenter.bridge;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarObjectNotFoundException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerArtifactCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerCategoryCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerFolderCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerResourceCurrentUserReport;
import org.metadatacenter.permission.currentuserpermission.CurrentUserCategoryPermissionUpdater;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.*;
import org.metadatacenter.server.permissions.CurrentUserPermissionUpdaterForGraphDbCategory;
import org.metadatacenter.server.permissions.CurrentUserPermissionUpdaterForGraphDbFolder;
import org.metadatacenter.server.permissions.CurrentUserPermissionUpdaterForGraphDbResource;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.auth.FolderWithCurrentUserPermissions;

public class GraphDbPermissionReader {

  private GraphDbPermissionReader() {
  }

  public static FolderServerArtifactCurrentUserReport getArtifactCurrentUserReport(CedarRequestContext context, FolderServiceSession folderSession,
                                                                                   ResourcePermissionServiceSession permissionSession,
                                                                                   CedarConfig cedarConfig, CedarArtifactId artifactId) throws CedarException {
    if (artifactId != null) {
      FolderServerArtifact artifact = folderSession.findArtifactById(artifactId);
      if (artifact == null) {
        throw new CedarObjectNotFoundException("The artifact can not be found by id")
            .errorKey(CedarErrorKey.ARTIFACT_NOT_FOUND)
            .parameter("id", artifactId);
      }

      folderSession.addPathAndParentId(artifact);

      artifact.setPathInfo(PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, artifact));

      FolderServerArtifactCurrentUserReport artifactReport =
          (FolderServerArtifactCurrentUserReport) FolderServerResourceCurrentUserReport.fromResource(artifact);

      decorateResourceWithCurrentUserPermissions(context, permissionSession, cedarConfig, artifactReport);

      return artifactReport;
    }
    return null;
  }

  public static void decorateResourceWithCurrentUserPermissions(CedarRequestContext context, ResourcePermissionServiceSession permissionSession,
                                                                CedarConfig cedarConfig, FilesystemResourceWithCurrentUserPermissions resource) {
    VersionServiceSession versionSession = CedarDataServices.getVersionServiceSession(context);
    CurrentUserPermissionUpdater cupu = CurrentUserPermissionUpdaterForGraphDbResource.get(permissionSession, versionSession, cedarConfig, resource);
    cupu.update(resource.getCurrentUserPermissions());
  }

  public static FolderServerFolderCurrentUserReport getFolderCurrentUserReport(CedarRequestContext context, FolderServiceSession folderSession,
                                                                               ResourcePermissionServiceSession permissionSession,
                                                                               CedarFolderId folderId) throws CedarException {
    if (folderId != null) {
      FolderServerFolder folder = folderSession.findFolderById(folderId);
      if (folder == null) {
        throw new CedarObjectNotFoundException("The folder can not be found by id")
            .errorKey(CedarErrorKey.FOLDER_NOT_FOUND)
            .parameter("id", folderId);
      }

      folderSession.addPathAndParentId(folder);

      folder.setPathInfo(PathInfoBuilder.getResourcePathExtract(context, folderSession, permissionSession, folder));

      FolderServerFolderCurrentUserReport folderReport =
          (FolderServerFolderCurrentUserReport) FolderServerResourceCurrentUserReport.fromResource(folder);

      decorateFolderWithCurrentUserPermissions(permissionSession, folderReport);

      return folderReport;
    }
    return null;
  }

  private static void decorateFolderWithCurrentUserPermissions(ResourcePermissionServiceSession permissionSession,
                                                               FolderWithCurrentUserPermissions folder) {
    CurrentUserPermissionUpdater cupu = CurrentUserPermissionUpdaterForGraphDbFolder.get(permissionSession, folder);
    cupu.update(folder.getCurrentUserPermissions());
  }

  public static FolderServerCategoryCurrentUserReport getCategoryCurrentUserReport(CategoryServiceSession categorySession,
                                                                                   CategoryPermissionServiceSession categoryPermissionSession,
                                                                                   CedarCategoryId categoryId) throws CedarException {
    if (categoryId != null) {
      FolderServerCategory category = categorySession.getCategoryById(categoryId);
      if (category == null) {
        throw new CedarObjectNotFoundException("The category can not be found by id")
            .errorKey(CedarErrorKey.CATEGORY_NOT_FOUND)
            .parameter("id", categoryId);
      }

      FolderServerCategoryCurrentUserReport categoryReport = FolderServerCategoryCurrentUserReport.fromCategory(category);

      decorateCategoryWithCurrentUserPermissions(categoryPermissionSession, categoryReport);

      return categoryReport;
    }
    return null;
  }

  private static void decorateCategoryWithCurrentUserPermissions(CategoryPermissionServiceSession categoryPermissionSession,
                                                                 FolderServerCategoryCurrentUserReport categoryReport) {
    CurrentUserCategoryPermissionUpdater cupu = CurrentUserPermissionUpdaterForGraphDbCategory.get(categoryPermissionSession, categoryReport);
    cupu.update(categoryReport.getCurrentUserPermissions());
  }

}
