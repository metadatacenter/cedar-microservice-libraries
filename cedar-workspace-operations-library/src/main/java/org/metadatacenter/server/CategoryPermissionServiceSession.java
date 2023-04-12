package org.metadatacenter.server;

import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionRequest;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;

public interface CategoryPermissionServiceSession {

  boolean userHasWriteAccessToCategory(CedarCategoryId categoryId);

  boolean userHasAttachAccessToCategory(CedarCategoryId categoryId);

  boolean userCanChangeOwnerOfCategory(CedarCategoryId categoryId);

  CategoryPermissions getCategoryPermissions(CedarCategoryId categoryId);

  BackendCallResult updateCategoryPermissions(CedarCategoryId categoryId, CategoryPermissionRequest permissionsRequest);

  boolean userIsOwnerOfCategory(CedarCategoryId categoryId);

  boolean userHas(CedarPermission permission);

}
