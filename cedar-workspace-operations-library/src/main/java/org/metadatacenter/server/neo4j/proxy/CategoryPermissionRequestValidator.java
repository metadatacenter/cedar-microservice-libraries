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
import org.metadatacenter.server.security.model.auth.CedarPermission;
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
      validateWritePermission();
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
      validateUserUniqueness();
    }
    if (callResult.isOk()) {
      validateGroupUniqueness();
    }
    if (callResult.isOk()) {
      validateOwnerAndUserCollision();
    }
    if (callResult.isOk()) {
      validateOwnerSetPermission();
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

  private void validateWritePermission() {
    if (!categoryPermissionService.userHasWriteAccessToCategory(categoryId)) {
      callResult.addError(AUTHORIZATION)
          .errorKey(CedarErrorKey.NO_WRITE_ACCESS_TO_CATEGORY)
          .message("The current user has no write access to the category")
          .parameter("categoryId", categoryId.getId());
    }
  }

  private void validateAndSetOwner() {
    CategoryPermissionUser owner = request.getOwner();
    if (owner == null) {
      callResult.addError(INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "owner")
          .message("The owner should be present in the request");
    } else {
      CedarUserId userId = CedarUserId.build(owner.getId());
      FolderServerUser newOwner = proxies.user().findUserById(userId);
      if (newOwner == null) {
        callResult.addError(NOT_FOUND)
            .errorKey(CedarErrorKey.USER_NOT_FOUND)
            .message("The new owner can not be found")
            .parameter("userId", userId.getId());
      } else {
        permissions.setOwner(newOwner.buildExtract());
      }
    }
  }

  private void validateAndSetUsers() {
    List<CategoryPermissionUserPermissionPair> userPermissions = request.getUserPermissions();
    for (CategoryPermissionUserPermissionPair pair : userPermissions) {
      CategoryPermissionUser permissionUser = pair.getUser();
      if (permissionUser == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "user")
            .message("The user resource is missing from the request");
      } else {
        CategoryPermission permission = pair.getPermission();
        if (permission == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "permission")
              .message("The permission is missing from the request");
        } else {
          CedarUserId userId = CedarUserId.build(permissionUser.getId());
          FolderServerUser user = proxies.user().findUserById(userId);
          if (user == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.USER_NOT_FOUND)
                .message("The user from request can not be found")
                .parameter("userId", userId.getId());

          } else {
            permissions.addUserPermissions(new CategoryUserPermission(user.buildExtract(), permission));
          }
        }
      }
    }
  }

  private void validateAndSetGroups() {
    List<CategoryPermissionGroupPermissionPair> groupPermissions = request.getGroupPermissions();
    for (CategoryPermissionGroupPermissionPair pair : groupPermissions) {
      CategoryPermissionGroup permissionGroup = pair.getGroup();
      if (permissionGroup == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "group")
            .message("The group resource is missing from the request");
      } else {
        CategoryPermission permission = pair.getPermission();
        if (permission == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "permission")
              .message("The permission is missing from the request");
        } else {
          CedarGroupId groupId = CedarGroupId.build(permissionGroup.getId());
          FolderServerGroup group = proxies.group().findGroupById(groupId);
          if (group == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.GROUP_NOT_FOUND)
                .message("The group from request can not be found")
                .parameter("groupId", groupId.getId());
          } else {
            permissions.addGroupPermissions(new CategoryGroupPermission(group.buildExtract(), permission));
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

  private void validateOwnerSetPermission() {
    String newOwnerId = permissions.getOwner().getId();
    CategoryPermissions currentPermissions = categoryPermissionService.getCategoryPermissions(categoryId);
    String currentOwnerId = currentPermissions.getOwner().getId();
    if (!newOwnerId.equals(currentOwnerId)) {
      // if it has the role, we do not check
      if (categoryPermissionService.userHas(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY)) {
        return;
      }
      if (!categoryPermissionService.userIsOwnerOfCategory(categoryId)) {
        callResult.addError(AUTHORIZATION)
            .errorKey(CedarErrorKey.NOT_AUTHORIZED)
            .message("Only the owner of a category can change the ownership")
            .parameter("categoryId", categoryId.getId());
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
