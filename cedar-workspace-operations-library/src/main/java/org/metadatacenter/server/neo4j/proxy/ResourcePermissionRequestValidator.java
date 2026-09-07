package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodeGroupPermission;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsWithExtract;
import org.metadatacenter.server.security.model.auth.CedarNodeUserPermission;
import org.metadatacenter.server.security.model.permission.resource.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.metadatacenter.error.CedarErrorType.*;

public class ResourcePermissionRequestValidator {

  private final ResourcePermissionsRequest request;
  private final ResourcePermissionServiceSession permissionService;
  private final Neo4JProxies proxies;
  private final BackendCallResult callResult;
  private final CedarNodePermissionsWithExtract permissions;
  private final CedarFilesystemResourceId resourceId;

  public ResourcePermissionRequestValidator(ResourcePermissionServiceSession permissionService, Neo4JProxies proxies,
                                            CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request) {
    this.permissionService = permissionService;
    this.proxies = proxies;
    this.callResult = new BackendCallResult();
    this.request = request;
    this.resourceId = resourceId;
    this.permissions = new CedarNodePermissionsWithExtract();

    validateNodeExistence();

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
      validateUserUniqueness();
    }
    if (callResult.isOk()) {
      validateGroupUniqueness();
    }
    if (callResult.isOk()) {
      validateEveryoneRole();
    }
    if (callResult.isOk()) {
      validateOwnerAndUserCollision();
    }
  }

  private void validateNodeExistence() {
    FileSystemResource folder = proxies.filesystemResource().findResourceById(resourceId);
    if (folder == null) {
      callResult.addError(NOT_FOUND)
          .errorKey(CedarErrorKey.NODE_NOT_FOUND)
          .message("Node not found by id")
          .parameter("nodeId", resourceId);
    }
  }

  private void validateGrantManagementAuthority() {
    if (!permissionService.userHasCapability(resourceId, ResourceCapability.MANAGE_GRANTS)) {
      callResult.addError(PERMISSION)
          .errorKey(CedarErrorKey.NOT_AUTHORIZED)
          .message("The current user may not manage grants on the resource")
          .parameter("nodeId", resourceId);
    }
  }

  private void validateRequest() {
    if (request == null) {
      callResult.addError(INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "request")
          .message("The resource permissions request is missing");
    }
  }

  private void validateAndSetOwner() {
    CedarNodePermissionsWithExtract currentPermissions = permissionService.getResourcePermissions(resourceId);
    if (currentPermissions == null || currentPermissions.getOwner() == null) {
      callResult.addError(SERVER_ERROR)
          .errorKey(CedarErrorKey.INVALID_DATA)
          .message("The resource does not have an owner")
          .parameter("nodeId", resourceId);
      return;
    }
    permissions.setOwner(currentPermissions.getOwner());

    ResourcePermissionUser suppliedOwner = request.getOwner();
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
    List<ResourcePermissionUserPermissionPair> userPermissions = request.getUserPermissions();
    if (userPermissions == null) {
      return;
    }
    for (ResourcePermissionUserPermissionPair pair : userPermissions) {
      if (pair == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userPermission")
            .message("The user permission entry is missing from the request");
        continue;
      }
      ResourcePermissionUser permissionUser = pair.getUser();
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
        ResourceRole role = pair.getRole();
        if (role == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "role")
              .message("The role is missing from the request");
        } else {
          CedarUserId userId = permissionUser.getResourceIds();
          FolderServerUser user = proxies.user().findUserById(userId);
          if (user == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.USER_NOT_FOUND)
                .message("The user from request can not be found")
                .parameter("userId", userId);

          } else {
            permissions.addUserPermissions(new CedarNodeUserPermission(user.buildExtract(), role));
          }
        }
      }
    }
  }

  private void validateAndSetGroups() {
    List<ResourcePermissionGroupPermissionPair> groupPermissions = request.getGroupPermissions();
    if (groupPermissions == null) {
      return;
    }
    for (ResourcePermissionGroupPermissionPair pair : groupPermissions) {
      if (pair == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "groupPermission")
            .message("The group permission entry is missing from the request");
        continue;
      }
      ResourcePermissionGroup permissionGroup = pair.getGroup();
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
        ResourceRole role = pair.getRole();
        if (role == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "role")
              .message("The role is missing from the request");
        } else {
          CedarGroupId groupId = permissionGroup.getResourceId();
          FolderServerGroup group = proxies.group().findGroupById(groupId);
          if (group == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.GROUP_NOT_FOUND)
                .message("The group from request can not be found")
                .parameter("groupId", groupId);
          } else {
            permissions.addGroupPermissions(new CedarNodeGroupPermission(group.buildExtract(), role));
          }
        }
      }
    }
  }

  private void validateUserUniqueness() {
    Set<String> userIds = new HashSet<>();
    for (CedarNodeUserPermission up : permissions.getUserPermissions()) {
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
    for (CedarNodeGroupPermission gp : permissions.getGroupPermissions()) {
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
    for (CedarNodeGroupPermission grant : permissions.getGroupPermissions()) {
      if (everyone.getId().equals(grant.getGroup().getId()) && grant.getRole() != ResourceRole.VIEWER) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.INVALID_DATA)
            .message("The Everyone group may only receive the Viewer role")
            .parameter("groupId", everyone.getId())
            .parameter("role", grant.getRole().getValue());
      }
    }
  }

  private void validateOwnerAndUserCollision() {
    String ownerId = permissions.getOwner().getId();
    for (CedarNodeUserPermission up : permissions.getUserPermissions()) {
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

  public CedarNodePermissionsWithExtract getPermissions() {
    return permissions;
  }
}
