package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.*;
import org.metadatacenter.server.security.model.user.CedarUser;

public class Neo4JUserSessionCategoryPermissionService extends AbstractNeo4JUserSession implements CategoryPermissionServiceSession {

  private Neo4JUserSessionCategoryPermissionService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId,
                                                    String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static CategoryPermissionServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId,
                                                     String localRequestId) {
    return new Neo4JUserSessionCategoryPermissionService(cedarConfig, proxies, cedarUser, globalRequestId,
        localRequestId);
  }

  @Override
  public CategoryPermissions getCategoryPermissions(CedarCategoryId categoryId) {
    VersionedCategoryPermissions versioned = getVersionedCategoryPermissions(categoryId);
    return versioned == null ? null : versioned.content();
  }

  @Override
  public VersionedCategoryPermissions getVersionedCategoryPermissions(CedarCategoryId categoryId) {
    return proxies.categoryPermission().getVersionedPermissions(categoryId);
  }

  @Override
  public BackendCallResult<VersionedCategoryPermissions> updateCategoryPermissions(
      CedarCategoryId categoryId, CategoryPermissionRequest request, RevisionPrecondition precondition) {
    CategoryPermissionRequestValidator prv = new CategoryPermissionRequestValidator(this, proxies, categoryId, request);
    BackendCallResult<VersionedCategoryPermissions> bcr = prv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CategoryPermissions newPermissions = prv.getPermissions();
      VersionedCategoryPermissions updated =
          proxies.categoryPermission().replacePermissions(categoryId, newPermissions, precondition);
      if (updated == null) {
        BackendCallResult<VersionedCategoryPermissions> failure = new BackendCallResult<>();
        failure.addError(org.metadatacenter.error.CedarErrorType.SERVER_ERROR)
            .message("The category permissions could not be updated");
        return failure;
      }
      bcr.setPayload(updated);
      return bcr;
    }
  }

  @Override
  public boolean userIsOwnerOfCategory(CedarCategoryId categoryId) {
    FolderServerUser owner = getCategoryOwner(categoryId);
    return owner != null && owner.getId().equals(cu.getId());
  }

  @Override
  public boolean userHasWriteAccessToCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      return proxies.categoryPermission().userHasWriteAccessToCategory(cu.getResourceId(), categoryId);
    }
  }

  @Override
  public boolean userHasAttachAccessToCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      return proxies.categoryPermission().userHasAttachAccessToCategory(cu.getResourceId(), categoryId);
    }
  }

  @Override
  public boolean userCanChangeOwnerOfCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      FolderServerUser owner = getCategoryOwner(categoryId);
      return owner != null && owner.getId().equals(cu.getId());
    }
  }

  @Override
  public boolean userHas(CedarPermission permission) {
    return cu.has(permission);
  }

}
