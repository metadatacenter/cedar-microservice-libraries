package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategoryPermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUserPermissionPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Neo4JProxyCategoryPermission extends AbstractNeo4JProxy {

  Neo4JProxyCategoryPermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  boolean updatePermissionsAtomically(CedarCategoryId categoryId, CedarUserId newOwnerId,
                                      Set<CategoryPermissionUserPermissionPair> removeUserPermissions,
                                      Set<CategoryPermissionUserPermissionPair> addUserPermissions,
                                      Set<CategoryPermissionGroupPermissionPair> removeGroupPermissions,
                                      Set<CategoryPermissionGroupPermissionPair> addGroupPermissions) {
    List<CypherQuery> changes = new ArrayList<>();

    if (newOwnerId != null) {
      changes.add(removeOwnerQuery(categoryId));
      changes.add(setOwnerQuery(categoryId, newOwnerId));
    }
    for (CategoryPermissionUserPermissionPair pair : removeUserPermissions) {
      changes.add(removePermissionQuery(categoryId, pair.getUser().getResourceId(), pair.getPermission()));
    }
    for (CategoryPermissionUserPermissionPair pair : addUserPermissions) {
      changes.add(addPermissionQuery(categoryId, pair.getUser().getResourceId(), pair.getPermission()));
    }
    for (CategoryPermissionGroupPermissionPair pair : removeGroupPermissions) {
      changes.add(removePermissionQuery(categoryId, pair.getGroup().getResourceId(), pair.getPermission()));
    }
    for (CategoryPermissionGroupPermissionPair pair : addGroupPermissions) {
      changes.add(addPermissionQuery(categoryId, pair.getGroup().getResourceId(), pair.getPermission()));
    }

    return executeWriteBatch(changes, "updating category permissions");
  }

  void addCategoryPermissionToUser(CedarCategoryId categoryId, CedarUserId userId, CategoryPermission permission) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        addCategoryPermission(categoryId, userId, permission);
      }
    }
  }

  void removeCategoryPermissionFromUser(CedarCategoryId categoryId, CedarUserId userId, CategoryPermission permission) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        removeCategoryPermission(categoryId, userId, permission);
      }
    }
  }

  void addCategoryPermissionToGroup(CedarCategoryId categoryId, CedarGroupId groupId, CategoryPermission permission) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        addCategoryPermission(categoryId, groupId, permission);
      }
    }
  }

  void removeCategoryPermissionFromGroup(CedarCategoryId categoryId, CedarGroupId groupId, CategoryPermission permission) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        removeCategoryPermission(categoryId, groupId, permission);
      }
    }
  }

  private boolean addCategoryPermission(CedarCategoryId categoryId, CedarUserId userId, CategoryPermission permission) {
    return executeWrite(addPermissionQuery(categoryId, userId, permission), "adding permission");
  }

  private boolean addCategoryPermission(CedarCategoryId category, CedarGroupId group, CategoryPermission permission) {
    return executeWrite(addPermissionQuery(category, group, permission), "adding permission");
  }

  private boolean removeCategoryPermission(CedarCategoryId categoryId, CedarUserId userId, CategoryPermission permission) {
    return executeWrite(removePermissionQuery(categoryId, userId, permission), "removing permission");
  }

  private boolean removeCategoryPermission(CedarCategoryId categoryId, CedarGroupId groupId, CategoryPermission permission) {
    return executeWrite(removePermissionQuery(categoryId, groupId, permission), "removing permission");
  }

  private CypherQuery removeOwnerQuery(CedarCategoryId categoryId) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategory.removeCategoryOwner(),
        CypherParamBuilderCategory.matchId(categoryId));
  }

  private CypherQuery setOwnerQuery(CedarCategoryId categoryId, CedarUserId userId) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategory.setCategoryOwner(),
        CypherParamBuilderCategory.matchCategoryAndUser(categoryId, userId));
  }

  private CypherQuery addPermissionQuery(CedarCategoryId categoryId, CedarUserId userId,
                                         CategoryPermission permission) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategoryPermission.addPermissionToCategoryForUser(permission),
        AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId));
  }

  private CypherQuery addPermissionQuery(CedarCategoryId categoryId, CedarGroupId groupId,
                                         CategoryPermission permission) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategoryPermission.addPermissionToCategoryForGroup(permission),
        AbstractCypherParamBuilder.matchGroupIdAndCategoryId(groupId, categoryId));
  }

  private CypherQuery removePermissionQuery(CedarCategoryId categoryId, CedarUserId userId,
                                            CategoryPermission permission) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategoryPermission.removePermissionForCategoryFromUser(permission),
        AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId));
  }

  private CypherQuery removePermissionQuery(CedarCategoryId categoryId, CedarGroupId groupId,
                                            CategoryPermission permission) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategoryPermission.removePermissionForCategoryFromGroup(permission),
        AbstractCypherParamBuilder.matchGroupIdAndCategoryId(groupId, categoryId));
  }

  public boolean userHasWriteAccessToCategory(CedarUserId userId, CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategoryPermission.userCanWriteCategory();
    CypherParameters params = AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser cedarFSUser = executeReadGetOne(q, FolderServerUser.class);
    return cedarFSUser != null;
  }

  public boolean userHasAttachAccessToCategory(CedarUserId userId, CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategoryPermission.userCanAttachCategory();
    CypherParameters params = AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser cedarFSUser = executeReadGetOne(q, FolderServerUser.class);
    return cedarFSUser != null;
  }

  public List<FolderServerUser> getUsersWithDirectPermissionOnCategory(CedarCategoryId categoryId, CategoryPermission permission) {
    RelationLabel relationLabel = switch (permission) {
      case ATTACH -> RelationLabel.CANATTACHCATEGORY;
      case WRITE -> RelationLabel.CANWRITECATEGORY;
    };
    String cypher = CypherQueryBuilderCategoryPermission.getUsersWithDirectPermissionOnCategory(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  public List<FolderServerGroup> getGroupsWithDirectPermissionOnCategory(CedarCategoryId categoryId, CategoryPermission permission) {
    RelationLabel relationLabel = switch (permission) {
      case ATTACH -> RelationLabel.CANATTACHCATEGORY;
      case WRITE -> RelationLabel.CANWRITE;
    };
    String cypher = CypherQueryBuilderCategoryPermission.getGroupsWithDirectPermissionOnCategory(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

}
