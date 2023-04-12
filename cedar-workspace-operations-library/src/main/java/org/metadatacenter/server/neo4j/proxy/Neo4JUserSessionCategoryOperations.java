package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUserPermissionPair;

import java.util.Set;

public final class Neo4JUserSessionCategoryOperations {

  private Neo4JUserSessionCategoryOperations() {
  }

  public static void updateCategoryOwner(Neo4JProxyCategory neo4JProxy, CedarCategoryId categoryId, CedarUserId newOwnerId) {
    neo4JProxy.updateCategoryOwner(categoryId, newOwnerId);
  }

  public static void removeCategoryUserPermissions(Neo4JProxyCategoryPermission neo4JProxy, CedarCategoryId categoryId,
                                                   Set<CategoryPermissionUserPermissionPair> toRemoveUserPermissions) {
    for (CategoryPermissionUserPermissionPair pair : toRemoveUserPermissions) {
      neo4JProxy.removeCategoryPermissionFromUser(categoryId, pair.getUser().getResourceId(), pair.getPermission());
    }
  }

  public static void addCategoryUserPermissions(Neo4JProxyCategoryPermission neo4JProxy, CedarCategoryId categoryId,
                                                Set<CategoryPermissionUserPermissionPair> toAddUserPermissions) {
    for (CategoryPermissionUserPermissionPair pair : toAddUserPermissions) {
      neo4JProxy.addCategoryPermissionToUser(categoryId, pair.getUser().getResourceId(), pair.getPermission());
    }
  }

  public static void removeCategoryGroupPermissions(Neo4JProxyCategoryPermission neo4JProxy, CedarCategoryId categoryId,
                                                    Set<CategoryPermissionGroupPermissionPair> toRemoveGroupPermissions) {
    for (CategoryPermissionGroupPermissionPair pair : toRemoveGroupPermissions) {
      neo4JProxy.removeCategoryPermissionFromGroup(categoryId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

  public static void addCategoryGroupPermissions(Neo4JProxyCategoryPermission neo4JProxy, CedarCategoryId categoryId,
                                                 Set<CategoryPermissionGroupPermissionPair> toAddGroupPermissions) {
    for (CategoryPermissionGroupPermissionPair pair : toAddGroupPermissions) {
      neo4JProxy.addCategoryPermissionToGroup(categoryId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

}
