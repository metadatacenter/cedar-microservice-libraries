package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.List;

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
      validateAndSetUsers();
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

  private void validateAndSetUsers() {
    List<CedarGroupUserRequest> requestUsers = request.getUsers();
    for (CedarGroupUserRequest u : requestUsers) {
      ResourcePermissionUser groupUser = u.getUser();
      if (groupUser == null) {
        callResult.addError(CedarErrorType.INVALID_ARGUMENT)
            .errorKey(CedarErrorKey.MISSING_PARAMETER)
            .parameter("paramName", "userNode")
            .message("The user resource is missing from the request");
      } else {
        users.addUser(new CedarGroupUser(
            new CedarUserExtract(groupUser.getId(), null, null, null), u.isAdministrator(), u.isMember())
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
