package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class GroupUsersRequestValidator {

  private final CedarGroupUsersRequest request;
  private final Neo4JUserSessionGroupService neo4JUserSessionGroupService;
  private final BackendCallResult callResult;
  private final CedarGroupUsers users;
  private final CedarGroupId groupId;

  public GroupUsersRequestValidator(Neo4JUserSessionGroupService neo4JUserSessionGroupService, CedarGroupId groupId, CedarGroupUsersRequest request) {
    this.neo4JUserSessionGroupService = neo4JUserSessionGroupService;
    this.callResult = new BackendCallResult();
    this.request = request;
    this.groupId = groupId;
    this.users = new CedarGroupUsers();

    validateNodeExistence();

    if (callResult.isOk()) {
      validateAdministratorPermission();
    }

    if (callResult.isOk()) {
      validateRequest();
    }

    if (callResult.isOk()) {
      validateAndSetUsers();
    }
  }

  private void validateRequest() {
    if (request == null) {
      callResult.addError(CedarErrorType.INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "request")
          .message("The group users request is missing");
    } else if (request.getUsers() == null) {
      callResult.addError(CedarErrorType.INVALID_ARGUMENT)
          .errorKey(CedarErrorKey.MISSING_PARAMETER)
          .parameter("paramName", "users")
          .message("The users list is missing from the request");
    }
  }

  private void validateNodeExistence() {
    FolderServerGroup group = neo4JUserSessionGroupService.findGroupById(groupId);
    if (group == null) {
      callResult.addError(CedarErrorType.NOT_FOUND)
          .errorKey(CedarErrorKey.GROUP_NOT_FOUND)
          .message("Group not found by id")
          .parameter("groupId", groupId);
    }
  }

  /**
   * Membership is the widest lever in the sharing model, so changing it requires administering the
   * group. GroupsResource enforces this at the HTTP layer; enforcing it here too means a non-HTTP
   * caller of updateGroupUsers cannot skip it. Mirrors ResourcePermissionRequestValidator, which
   * refuses an ACL change from a caller without write access. Checked after existence so a missing
   * group is still a 404 rather than a 403.
   */
  private void validateAdministratorPermission() {
    if (!neo4JUserSessionGroupService.userCanAdministerGroup(groupId)) {
      // PERMISSION, not AUTHORIZATION: the caller is identified and simply lacks authority over this
      // group, which is 403. AUTHORIZATION maps to 401 and would tell them to authenticate again, which
      // cannot help. ResourcePermissionRequestValidator answers its equivalent denial the same way.
      callResult.addError(CedarErrorType.PERMISSION)
          .errorKey(CedarErrorKey.GROUP_CAN_BY_MODIFIED_ONLY_BY_GROUP_ADMIN)
          .message("Only the administrators can update the group!")
          .parameter("groupId", groupId);
    }
  }

  private void validateAndSetUsers() {
    List<CedarGroupUserRequest> requestUsers = request.getUsers();
    Set<String> userIds = new HashSet<>();
    for (CedarGroupUserRequest u : requestUsers) {
      if (u == null) {
        callResult.addError(CedarErrorType.INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userEntry")
            .message("A user entry is missing from the request");
        continue;
      }
      ResourcePermissionUser groupUser = u.getUser();
      if (groupUser == null) {
        callResult.addError(CedarErrorType.INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userNode")
            .message("The user resource is missing from the request");
      } else {
        String userId = groupUser.getId();
        if (userId == null || userId.isBlank()) {
          callResult.addError(CedarErrorType.INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.MISSING_PARAMETER)
              .parameter("paramName", "userId")
              .message("The user id is missing from the request");
          continue;
        }
        if (!userIds.add(userId)) {
          callResult.addError(CedarErrorType.INVALID_ARGUMENT)
              .errorKey(CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION)
              .parameter("propertyName", "userId")
              .parameter("userId", userId)
              .message("Each user should be listed only once in the request");
          continue;
        }
        // An unknown user fails the whole request. The relation queries match on id and affect nothing
        // when the node is absent, so without this the named user was quietly dropped while the rest of
        // the membership change went through and the caller was told it had all been applied.
        if (!neo4JUserSessionGroupService.userExists(CedarUserId.build(userId))) {
          callResult.addError(CedarErrorType.NOT_FOUND)
              .errorKey(CedarErrorKey.USER_NOT_FOUND)
              .parameter("userId", userId)
              .message("The user can not be found by id");
          continue;
        }
        users.addUser(new CedarGroupUser(
            new CedarUserExtract(userId, null, null, null), u.isAdministrator(), u.isMember())
        );
      }
    }
  }

  public BackendCallResult getCallResult() {
    return callResult;
  }

  public CedarGroupUsers getUsers() {
    return users;
  }
}
