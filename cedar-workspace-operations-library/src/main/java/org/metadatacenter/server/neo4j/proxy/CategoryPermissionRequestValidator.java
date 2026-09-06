package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.category.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.metadatacenter.error.CedarErrorType.*;

public class CategoryPermissionRequestValidator {

  private final CategoryPermissionRequest request;
  private final CategoryPermissionServiceSession categoryPermissionService;
  private final Neo4JProxies proxies;
  private final BackendCallResult callResult;
  private final CategoryPermissions permissions;
  private final CedarCategoryId categoryId;

  private FolderServerCategory category;

  public CategoryPermissionRequestValidator(CategoryPermissionServiceSession categoryPermissionService, Neo4JProxies proxies,
                                            CedarCategoryId categoryId, CategoryPermissionRequest request) {
    this.categoryPermissionService = categoryPermissionService;
    this.proxies = proxies;
    this.callResult = new BackendCallResult();
    this.request = request;
    this.categoryId = categoryId;
    this.category = null;
    this.permissions = new CategoryPermissions();

    validateCategoryExistence();

    if (callResult.isOk()) {
      validateGrantManagementAuthority();
    }
    if (callResult.isOk()) {
      validateRequest();
    }
    if (callResult.isOk()) {
      validateAndSetOwner();
    }
    if (callResult.isOk()) {
      validateAndSetUsers();
    }
    if (callResult.isOk()) {
      validateAndSetGroups();
    }
    if (callResult.isOk()) {
      ensureRootEveryoneViewer();
    }
    if (callResult.isOk()) {
      validateUserUniqueness();
    }
    if (callResult.isOk()) {
      validateGroupUniqueness();
    }
    if (callResult.isOk()) {
      validateOwnerAndUserCollision();
    }
    if (callResult.isOk()) {
      validateEveryoneRole();
    }
  }

  private void validateCategoryExistence() {
    this.category = proxies.category().getCategoryById(categoryId);
    if (category == null) {
      callResult.addError(NOT_FOUND)
          .errorKey(CedarErrorKey.CATEGORY_NOT_FOUND)
          .message("Category not found by id")
          .parameter("categoryId", categoryId.getId());
    }
  }

  private void validateGrantManagementAuthority() {
    if (!categoryPermissionService.userHasCapability(categoryId, CategoryCapability.MANAGE_GRANTS)) {
      callResult.addError(PERMISSION)
          .errorKey(CedarErrorKey.NOT_AUTHORIZED)
          .message("The current user may not manage grants on the category")
          .parameter("categoryId", categoryId.getId());
    }
  }

  private void validateRequest() {
    if (request == null) {
      callResult.addError(INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "request")
          .message("The category permissions request is missing");
    }
  }

  private void validateAndSetOwner() {
    CategoryPermissions currentPermissions = categoryPermissionService.getCategoryPermissions(categoryId);
    if (currentPermissions == null || currentPermissions.getOwner() == null) {
      callResult.addError(SERVER_ERROR)
          .errorKey(CedarErrorKey.INVALID_DATA)
          .message("The category does not have an owner")
          .parameter("categoryId", categoryId.getId());
      return;
    }
    permissions.setOwner(currentPermissions.getOwner());

    CategoryPermissionUser suppliedOwner = request.getOwner();
    if (suppliedOwner != null && suppliedOwner.getId() != null
        && !suppliedOwner.getId().equals(currentPermissions.getOwner().getId())) {
      callResult.addError(INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.INVALID_DATA)
          .message("Ownership cannot be changed through an ACL update; use ownership transfer")
          .parameter("currentOwnerId", currentPermissions.getOwner().getId())
          .parameter("requestedOwnerId", suppliedOwner.getId());
    }
  }

  private void validateAndSetUsers() {
    List<CategoryPermissionUserPermissionPair> userPermissions = request.getUserPermissions();
    if (userPermissions == null) {
      return;
    }
    for (CategoryPermissionUserPermissionPair pair : userPermissions) {
      if (pair == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userPermission")
            .message("The user permission entry is missing from the request");
        continue;
      }
      CategoryPermissionUser permissionUser = pair.getUser();
      if (permissionUser == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "user")
            .message("The user resource is missing from the request");
      } else if (permissionUser.getId() == null || permissionUser.getId().isBlank()) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userId")
            .message("The user id is missing from the request");
      } else {
        CategoryRole role = pair.getRole();
        if (role == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "role")
              .message("The role is missing from the request");
        } else {
          CedarUserId userId = CedarUserId.build(permissionUser.getId());
          FolderServerUser user = proxies.user().findUserById(userId);
          if (user == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.USER_NOT_FOUND)
                .message("The user from request can not be found")
                .parameter("userId", userId.getId());

          } else {
            permissions.addUserPermissions(new CategoryUserPermission(user.buildExtract(), role));
          }
        }
      }
    }
  }

  private void validateAndSetGroups() {
    List<CategoryPermissionGroupPermissionPair> groupPermissions = request.getGroupPermissions();
    if (groupPermissions == null) {
      return;
    }
    for (CategoryPermissionGroupPermissionPair pair : groupPermissions) {
      if (pair == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "groupPermission")
            .message("The group permission entry is missing from the request");
        continue;
      }
      CategoryPermissionGroup permissionGroup = pair.getGroup();
      if (permissionGroup == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "group")
            .message("The group resource is missing from the request");
      } else if (permissionGroup.getId() == null || permissionGroup.getId().isBlank()) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "groupId")
            .message("The group id is missing from the request");
      } else {
        CategoryRole role = pair.getRole();
        if (role == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "role")
              .message("The role is missing from the request");
        } else {
          CedarGroupId groupId = CedarGroupId.build(permissionGroup.getId());
          FolderServerGroup group = proxies.group().findGroupById(groupId);
          if (group == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.GROUP_NOT_FOUND)
                .message("The group from request can not be found")
                .parameter("groupId", groupId.getId());
          } else {
            permissions.addGroupPermissions(new CategoryGroupPermission(group.buildExtract(), role));
          }
        }
      }
    }
  }

  private void validateUserUniqueness() {
    Set<String> userIds = new HashSet<>();
    for (CategoryUserPermission up : permissions.getUserPermissions()) {
      String uid = up.getUser().getId();
      if (userIds.contains(uid)) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION)
            .message("Each user should be listed only once in the request")
            .parameter("propertyName", "userId")
            .parameter("userId", uid);
      } else {
        userIds.add(uid);
      }
    }
  }

  private void validateGroupUniqueness() {
    Set<String> groupIds = new HashSet<>();
    for (CategoryGroupPermission gp : permissions.getGroupPermissions()) {
      String gid = gp.getGroup().getId();
      if (groupIds.contains(gid)) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION)
            .message("Each group should be listed only once in the request")
            .parameter("propertyName", "groupId")
            .parameter("groupId", gid);
      } else {
        groupIds.add(gid);
      }
    }
  }

  private void validateEveryoneRole() {
    FolderServerGroup everyone = proxies.group().getEverybodyGroup();
    if (everyone == null) {
      return;
    }
    for (CategoryGroupPermission grant : permissions.getGroupPermissions()) {
      if (everyone.getId().equals(grant.getGroup().getId()) && grant.getRole() != CategoryRole.VIEWER) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.INVALID_DATA)
            .message("The Everyone group may only receive the Viewer role")
            .parameter("groupId", everyone.getId())
            .parameter("role", grant.getRole().getValue());
      }
    }
  }

  private void ensureRootEveryoneViewer() {
    if (category.getParentCategoryId() != null) {
      return;
    }
    FolderServerGroup everyone = proxies.group().getEverybodyGroup();
    if (everyone == null) {
      callResult.addError(SERVER_ERROR)
          .errorKey(CedarErrorKey.INVALID_DATA)
          .message("The Everyone group is required before root category grants can be changed")
          .parameter("categoryId", categoryId.getId());
      return;
    }
    boolean present = permissions.getGroupPermissions().stream()
        .anyMatch(grant -> everyone.getId().equals(grant.getGroup().getId()));
    if (!present) {
      permissions.addGroupPermissions(new CategoryGroupPermission(everyone.buildExtract(), CategoryRole.VIEWER));
    }
  }

  private void validateOwnerAndUserCollision() {
    String ownerId = permissions.getOwner().getId();
    for (CategoryUserPermission up : permissions.getUserPermissions()) {
      if (ownerId.equals(up.getUser().getId())) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.INVALID_DATA)
            .message("The owner should not be listed among the user permissions")
            .parameter("userId", ownerId);
      }
    }
  }

  public BackendCallResult getCallResult() {
    return callResult;
  }

  public CategoryPermissions getPermissions() {
    return permissions;
  }
}
