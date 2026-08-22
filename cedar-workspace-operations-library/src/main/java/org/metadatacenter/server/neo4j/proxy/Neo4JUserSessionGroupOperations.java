package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.security.model.auth.CedarGroupUser;
import org.metadatacenter.server.security.model.auth.CedarGroupUsers;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Neo4JUserSessionGroupOperations {

  enum Filter {
    ADMINISTRATOR, MEMBER
  }

  private Neo4JUserSessionGroupOperations() {
  }

  /**
   * The queries that carry one relation of a group's membership from its current state to the
   * requested one. Returning them rather than running them lets the caller commit the administrator
   * and member changes together, in a single transaction.
   */
  static List<CypherQuery> collectGroupUserChanges(Neo4JProxyGroup neo4JProxy, CedarGroupId groupId,
                                                   CedarGroupUsers currentGroupUsers, CedarGroupUsers newGroupUsers,
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

    List<CypherQuery> changes = new ArrayList<>();

    Set<CedarUserId> toRemoveUsers = new HashSet<>(oldUsers);
    toRemoveUsers.removeAll(newUsers);
    for (CedarUserId cuid : toRemoveUsers) {
      changes.add(neo4JProxy.removeUserGroupRelationQuery(cuid, groupId, relation));
    }

    Set<CedarUserId> toAddUsers = new HashSet<>(newUsers);
    toAddUsers.removeAll(oldUsers);
    for (CedarUserId cuid : toAddUsers) {
      changes.add(neo4JProxy.addUserGroupRelationQuery(cuid, groupId, relation));
    }

    return changes;
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
