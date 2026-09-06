package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.*;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.Collections;
import java.util.Set;

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
  public BackendCallResult<VersionedCategoryPermissions> transferCategoryOwnership(
      CedarCategoryId categoryId, CedarUserId newOwnerId, RevisionPrecondition precondition) {
    BackendCallResult<VersionedCategoryPermissions> result = new BackendCallResult<>();
    FolderServerCategory category = proxies.category().getCategoryById(categoryId);
    if (category == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.NOT_FOUND)
          .errorKey(org.metadatacenter.error.CedarErrorKey.CATEGORY_NOT_FOUND)
          .message("Category not found")
          .parameter("categoryId", categoryId);
      return result;
    }
    if (category.getParentCategoryId() == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.INVALID_ARGUMENT)
          .errorKey(org.metadatacenter.error.CedarErrorKey.INVALID_INPUT)
          .message("The root category cannot be transferred")
          .parameter("categoryId", categoryId);
      return result;
    }
    if (!userIsOwnerOfCategory(categoryId)) {
      result.addError(org.metadatacenter.error.CedarErrorType.PERMISSION)
          .errorKey(org.metadatacenter.error.CedarErrorKey.NOT_AUTHORIZED)
          .message("Only the current owner may transfer category ownership")
          .parameter("categoryId", categoryId);
      return result;
    }
    FolderServerUser newOwner = proxies.user().findUserById(newOwnerId);
    if (newOwner == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.NOT_FOUND)
          .errorKey(org.metadatacenter.error.CedarErrorKey.USER_NOT_FOUND)
          .message("The new owner could not be found")
          .parameter("userId", newOwnerId);
      return result;
    }
    if (cu.getId().equals(newOwner.getId())) {
      result.addError(org.metadatacenter.error.CedarErrorType.INVALID_ARGUMENT)
          .errorKey(org.metadatacenter.error.CedarErrorKey.INVALID_INPUT)
          .message("The new owner must be a different user")
          .parameter("userId", newOwnerId);
      return result;
    }
    VersionedCategoryPermissions transferred = proxies.categoryPermission().transferOwnership(
        categoryId, cu.getResourceId(), newOwnerId, precondition);
    if (transferred == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.SERVER_ERROR)
          .message("Category ownership could not be transferred")
          .parameter("categoryId", categoryId);
      return result;
    }
    result.setPayload(transferred);
    return result;
  }

  @Override
  public boolean userIsOwnerOfCategory(CedarCategoryId categoryId) {
    FolderServerUser owner = getCategoryOwner(categoryId);
    return owner != null && owner.getId().equals(cu.getId());
  }

  @Override
  public CategoryAuthority getCategoryAuthority(CedarCategoryId categoryId) {
    return proxies.categoryPermission().getCategoryAuthority(cu.getResourceId(), categoryId);
  }

  @Override
  public Set<CategoryCapability> getCategoryCapabilities(CedarCategoryId categoryId) {
    FolderServerCategory category = proxies.category().getCategoryById(categoryId);
    if (category == null) {
      return Collections.emptySet();
    }
    CategoryAccessContext context = new CategoryAccessContext(category.getParentCategoryId() == null);
    return CategoryCapabilityPolicy.evaluate(getCategoryAuthority(categoryId), context, cu);
  }

  @Override
  public boolean userHasRole(CedarCategoryId categoryId, CategoryRole requiredRole) {
    return getCategoryAuthority(categoryId).satisfies(requiredRole);
  }

  @Override
  public boolean userHasCapability(CedarCategoryId categoryId, CategoryCapability capability) {
    return getCategoryCapabilities(categoryId).contains(capability);
  }

  @Override
  public boolean userHas(CedarPermission permission) {
    return cu.has(permission);
  }

}
