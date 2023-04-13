package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.auth.CedarGroupUser;
import org.metadatacenter.server.security.model.auth.CedarGroupUsers;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;

import java.util.HashSet;
import java.util.Set;

public final class Neo4JUserSessionGroupOperations {

  enum Filter {
    ADMINISTRATOR, MEMBER
  }

  private Neo4JUserSessionGroupOperations() {
  }

  static void updateGroupUsers(Neo4JProxyGroup neo4JProxy, CedarGroupId groupId, CedarGroupUsers currentGroupUsers, CedarGroupUsers newGroupUsers,
                               RelationLabel relation, Filter filter) {
    Set<CedarUserId> oldUsers = new HashSet<>();
    for (CedarGroupUser gu : currentGroupUsers.getUsers()) {
      if ((filter == Filter.ADMINISTRATOR && gu.isAdministrator()) || (filter == Filter.MEMBER && gu.isMember())) {
        oldUsers.add(gu.getResourceId());
      }
    }
    Set<CedarUserId> newUsers = new HashSet<>();
    for (CedarGroupUser gu : newGroupUsers.getUsers()) {
      if ((filter == Filter.ADMINISTRATOR && gu.isAdministrator()) || (filter == Filter.MEMBER && gu.isMember())) {
        newUsers.add(gu.getResourceId());
      }
    }

    Set<CedarUserId> toRemoveUsers = new HashSet<>(oldUsers);
    toRemoveUsers.removeAll(newUsers);
    if (!toRemoveUsers.isEmpty()) {
      removeGroupUsers(neo4JProxy, groupId, toRemoveUsers, relation);
    }

    Set<CedarUserId> toAddUsers = new HashSet<>();
    toAddUsers.addAll(newUsers);
    toAddUsers.removeAll(oldUsers);
    if (!toAddUsers.isEmpty()) {
      addGroupUsers(neo4JProxy, groupId, toAddUsers, relation);
    }
  }

  private static void addGroupUsers(Neo4JProxyGroup neo4JProxy, CedarGroupId groupURL, Set<CedarUserId> toAddUsers, RelationLabel relation) {
    for (CedarUserId cuid : toAddUsers) {
      neo4JProxy.addUserGroupRelation(cuid, groupURL, relation);
    }
  }

  private static void removeGroupUsers(Neo4JProxyGroup neo4JProxy, CedarGroupId groupURL, Set<CedarUserId> toRemoveUsers, RelationLabel relation) {
    for (CedarUserId cuid : toRemoveUsers) {
      neo4JProxy.removeUserGroupRelation(cuid, groupURL, relation);
    }
  }

  static void addGroupPermissions(Neo4JProxyResourcePermission neo4JProxy, CedarFilesystemResourceId resourceId,
                                  Set<ResourcePermissionGroupPermissionPair> toAddGroupPermissions) {
    for (ResourcePermissionGroupPermissionPair pair : toAddGroupPermissions) {
      neo4JProxy.addPermissionToGroup(resourceId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

  static void removeGroupPermissions(Neo4JProxyResourcePermission neo4JProxy, CedarFilesystemResourceId resourceId,
                                     Set<ResourcePermissionGroupPermissionPair> toRemoveGroupPermissions) {
    for (ResourcePermissionGroupPermissionPair pair : toRemoveGroupPermissions) {
      neo4JProxy.removePermissionFromGroup(resourceId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

  static void addUserPermissions(Neo4JProxyResourcePermission neo4JProxy, CedarFilesystemResourceId resourceId,
                                 Set<ResourcePermissionUserPermissionPair> toAddUserPermissions) {
    for (ResourcePermissionUserPermissionPair pair : toAddUserPermissions) {
      neo4JProxy.addPermissionToUser(resourceId, pair.getUser().getResourceIds(), pair.getPermission());
    }
  }

}
