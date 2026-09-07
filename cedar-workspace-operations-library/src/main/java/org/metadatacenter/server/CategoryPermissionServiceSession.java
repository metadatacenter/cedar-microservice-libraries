package org.metadatacenter.server;

import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryCapability;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionRequest;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;

import java.util.Set;

public interface CategoryPermissionServiceSession {

  CategoryAuthority getCategoryAuthority(CedarCategoryId categoryId);

  Set<CategoryCapability> getCategoryCapabilities(CedarCategoryId categoryId);

  boolean userHasRole(CedarCategoryId categoryId, CategoryRole requiredRole);

  boolean userHasCapability(CedarCategoryId categoryId, CategoryCapability capability);

  CategoryPermissions getCategoryPermissions(CedarCategoryId categoryId);

  VersionedCategoryPermissions getVersionedCategoryPermissions(CedarCategoryId categoryId);

  default BackendCallResult<VersionedCategoryPermissions> updateCategoryPermissions(
      CedarCategoryId categoryId, CategoryPermissionRequest permissionsRequest) {
    return updateCategoryPermissions(categoryId, permissionsRequest, RevisionPrecondition.any());
  }

  BackendCallResult<VersionedCategoryPermissions> updateCategoryPermissions(
      CedarCategoryId categoryId, CategoryPermissionRequest permissionsRequest, RevisionPrecondition precondition);

  BackendCallResult<VersionedCategoryPermissions> transferCategoryOwnership(
      CedarCategoryId categoryId, CedarUserId newOwnerId, RevisionPrecondition precondition);

  boolean userIsOwnerOfCategory(CedarCategoryId categoryId);

  boolean userHas(CedarPermission permission);

}
