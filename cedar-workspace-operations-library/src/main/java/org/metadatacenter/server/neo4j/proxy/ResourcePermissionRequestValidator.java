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
import org.metadatacenter.server.security.model.auth.CedarPermission;
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

  private FileSystemResource resource;

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

  private void validateNodeExistence() {
    FileSystemResource folder = proxies.filesystemResource().findResourceById(resourceId);
    resource = folder;
    if (folder == null) {
      callResult.addError(NOT_FOUND)
          .errorKey(CedarErrorKey.NODE_NOT_FOUND)
          .message("Node not found by id")
          .parameter("nodeId", resourceId);
    }
  }

  private void validateWritePermission() {
    if (!permissionService.userHasWriteAccessToResource(resourceId)) {
      callResult.addError(AUTHORIZATION)
          .errorKey(CedarErrorKey.NO_WRITE_ACCESS_TO_RESOURCE)
          .message("The current user has no write access to the resource")
          .parameter("nodeId", resourceId);
    }
  }

  private void validateAndSetOwner() {
    ResourcePermissionUser owner = request.getOwner();
    if (owner == null) {
      callResult.addError(INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "owner")
          .message("The owner should be present in the request");
    } else {
      CedarUserId newOwnerId = owner.getResourceIds();
      FolderServerUser newOwner = proxies.user().findUserById(newOwnerId);
      if (newOwner == null) {
        callResult.addError(NOT_FOUND)
            .errorKey(CedarErrorKey.USER_NOT_FOUND)
            .message("The new owner can not be found")
            .parameter("userId", newOwnerId);
      } else {
        permissions.setOwner(newOwner.buildExtract());
      }
    }
  }

  private void validateAndSetUsers() {
    List<ResourcePermissionUserPermissionPair> userPermissions = request.getUserPermissions();
    for (ResourcePermissionUserPermissionPair pair : userPermissions) {
      ResourcePermissionUser permissionUser = pair.getUser();
      if (permissionUser == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "user")
            .message("The user resource is missing from the request");
      } else {
        FilesystemResourcePermission permission = pair.getPermission();
        if (permission == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "permission")
              .message("The permission is missing from the request");
        } else {
          CedarUserId userId = permissionUser.getResourceIds();
          FolderServerUser user = proxies.user().findUserById(userId);
          if (user == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.USER_NOT_FOUND)
                .message("The user from request can not be found")
                .parameter("userId", userId);

          } else {
            permissions.addUserPermissions(new CedarNodeUserPermission(user.buildExtract(), permission));
          }
        }
      }
    }
  }

  private void validateAndSetGroups() {
    List<ResourcePermissionGroupPermissionPair> groupPermissions = request.getGroupPermissions();
    for (ResourcePermissionGroupPermissionPair pair : groupPermissions) {
      ResourcePermissionGroup permissionGroup = pair.getGroup();
      if (permissionGroup == null) {
        callResult.addError(INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "group")
            .message("The group resource is missing from the request");
      } else {
        FilesystemResourcePermission permission = pair.getPermission();
        if (permission == null) {
          callResult.addError(INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "permission")
              .message("The permission is missing from the request");
        } else {
          CedarGroupId groupId = permissionGroup.getResourceId();
          FolderServerGroup group = proxies.group().findGroupById(groupId);
          if (group == null) {
            callResult.addError(NOT_FOUND)
                .errorKey(CedarErrorKey.GROUP_NOT_FOUND)
                .message("The group from request can not be found")
                .parameter("groupId", groupId);
          } else {
            permissions.addGroupPermissions(new CedarNodeGroupPermission(group.buildExtract(), permission));
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

  private void validateOwnerSetPermission() {
    String newOwnerId = permissions.getOwner().getId();
    CedarNodePermissionsWithExtract currentPermissions = permissionService.getResourcePermissions(resourceId);
    String currentOwnerId = currentPermissions.getOwner().getId();
    if (!newOwnerId.equals(currentOwnerId)) {
      // if it has the role, we do not check
      if (permissionService.userHasPermission(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE)) {
        return;
      }
      if (!permissionService.userIsOwnerOfResource(resource.getResourceId())) {
        callResult.addError(AUTHORIZATION)
            .errorKey(CedarErrorKey.NOT_AUTHORIZED)
            .message("Only the owner of a resource can change the ownership")
            .parameter("nodeId", resourceId);
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
