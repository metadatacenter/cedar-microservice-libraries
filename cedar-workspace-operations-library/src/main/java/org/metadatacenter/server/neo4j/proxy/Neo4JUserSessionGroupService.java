package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUser;
import org.metadatacenter.server.security.model.auth.CedarGroupUsers;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.*;

public class Neo4JUserSessionGroupService extends AbstractNeo4JUserSession implements GroupServiceSession {

  private Neo4JUserSessionGroupService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu,
                                       String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static GroupServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser,
                                        String globalRequestId,
                                        String localRequestId) {
    return new Neo4JUserSessionGroupService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public List<FolderServerGroup> findGroups() {
    return proxies.group().findGroups();
  }

  @Override
  public FolderServerGroup findGroupById(CedarGroupId groupId) {
    return proxies.group().findGroupById(groupId);
  }

  @Override
  public FolderServerGroup findGroupByName(String groupName) {
    return proxies.group().findGroupByName(groupName);
  }

  @Override
  public FolderServerGroup createGroup(String groupName, String groupDescription) {
    String gid = linkedDataUtil.buildNewLinkedDataId(CedarResourceType.GROUP);
    CedarGroupId groupId = null;
    groupId = CedarGroupId.build(gid);
    return proxies.group().createGroup(groupId, groupName, groupDescription, cu.getResourceId(), null);
  }

  @Override
  public FolderServerGroup updateGroupById(CedarGroupId groupId, Map<NodeProperty, String> updateFields) {
    // Session-layer backstop for the HTTP administrator check (defense in depth): a caller reaching
    // this method without authority to administer the group makes no change and gets nothing back.
    if (!userCanAdministerGroup(groupId)) {
      return null;
    }
    return proxies.group().updateGroupById(groupId, updateFields, cu.getResourceId());
  }

  @Override
  public boolean deleteGroupById(CedarGroupId groupId) {
    if (!userCanAdministerGroup(groupId)) {
      return false;
    }
    return proxies.group().deleteGroupById(groupId);
  }

  @Override
  public BackendCallResult updateGroupUsers(CedarGroupId groupId, CedarGroupUsersRequest request) {

    GroupUsersRequestValidator gurv = new GroupUsersRequestValidator(this, groupId, request);
    BackendCallResult bcr = gurv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CedarGroupUsers currentGroupUsers = findGroupUsers(groupId);
      CedarGroupUsers newGroupUsers = gurv.getUsers();

      List<CypherQuery> changes = new ArrayList<>();
      changes.addAll(Neo4JUserSessionGroupOperations.collectGroupUserChanges(proxies.group(), groupId,
          currentGroupUsers, newGroupUsers, RelationLabel.ADMINISTERS,
          Neo4JUserSessionGroupOperations.Filter.ADMINISTRATOR));
      changes.addAll(Neo4JUserSessionGroupOperations.collectGroupUserChanges(proxies.group(), groupId,
          currentGroupUsers, newGroupUsers, RelationLabel.MEMBEROF,
          Neo4JUserSessionGroupOperations.Filter.MEMBER));

      // A request describes one membership change, so the administrator and member relations commit
      // together. Applied one statement at a time, a failure partway through left the group in a
      // state the caller never asked for and could not see, since the error came back as a 500.
      if (!proxies.group().applyUserGroupRelationChanges(changes)) {
        BackendCallResult failure = new BackendCallResult();
        failure.addError(CedarErrorType.SERVER_ERROR)
            .errorKey(CedarErrorKey.GROUP_USERS_NOT_UPDATED)
            .message("The group members could not be updated")
            .parameter("groupId", groupId);
        return failure;
      }

      return new BackendCallResult();
    }
  }

  @Override
  public CedarGroupUsers findGroupUsers(CedarGroupId groupId) {
    Set<String> memberIds = new HashSet<>();
    Set<String> administratorsIds = new HashSet<>();
    Map<String, FolderServerUser> users = new HashMap<>();
    List<FolderServerUser> groupMembers = proxies.group().findGroupMembers(groupId);
    for (FolderServerUser member : groupMembers) {
      memberIds.add(member.getId());
      users.put(member.getId(), member);
    }
    List<FolderServerUser> groupAdministrators = proxies.group().findGroupAdministrators(groupId);
    for (FolderServerUser administrator : groupAdministrators) {
      administratorsIds.add(administrator.getId());
      users.put(administrator.getId(), administrator);
    }
    CedarGroupUsers ret = new CedarGroupUsers();
    for (String userURL : users.keySet()) {
      ret.addUser(new CedarGroupUser(users.get(userURL).buildExtract(),
          administratorsIds.contains(userURL),
          memberIds.contains(userURL))
      );
    }
    return ret;
  }

  /**
   * Whether a user node exists for this id. GroupUsersRequestValidator needs it to refuse a
   * membership request naming a user the graph does not hold: the relation queries match on id, so an
   * unknown user would otherwise be skipped without a word and the request still reported as applied.
   */
  public boolean userExists(CedarUserId userId) {
    return proxies.user().findUserById(userId) != null;
  }

  /**
   * Whether the current user may administer this group: either they administer it directly, or they
   * hold the privileged override (built-in admin). This is the authority the HTTP layer already checks
   * inline in GroupsResource; enforcing it here too closes the gap for any non-HTTP caller.
   */
  public boolean userCanAdministerGroup(CedarGroupId groupId) {
    return userAdministersGroup(groupId) || cu.has(CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP);
  }

  @Override
  public boolean userAdministersGroup(CedarGroupId groupId) {
    CedarGroupUsers groupUsers = findGroupUsers(groupId);
    if (groupUsers != null) {
      String currentUserId = cu.getId();
      for (CedarGroupUser user : groupUsers.getUsers()) {
        if (currentUserId.equals(user.getUser().getId()) && user.isAdministrator()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public long getGroupCount() {
    return proxies.group().getGroupCount();
  }
}
