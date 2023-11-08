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
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategoryPermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;

import java.util.List;

public class Neo4JProxyCategoryPermission extends AbstractNeo4JProxy {

  Neo4JProxyCategoryPermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
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
    String cypher = CypherQueryBuilderCategoryPermission.addPermissionToCategoryForUser(permission);
    CypherParameters params = AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding permission");
  }

  private boolean addCategoryPermission(CedarCategoryId category, CedarGroupId group, CategoryPermission permission) {
    String cypher = CypherQueryBuilderCategoryPermission.addPermissionToCategoryForGroup(permission);
    CypherParameters params = AbstractCypherParamBuilder.matchGroupIdAndCategoryId(group, category);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding permission");
  }

  private boolean removeCategoryPermission(CedarCategoryId categoryId, CedarUserId userId, CategoryPermission permission) {
    String cypher = CypherQueryBuilderCategoryPermission.removePermissionForCategoryFromUser(permission);
    CypherParameters params = AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing permission");
  }

  private boolean removeCategoryPermission(CedarCategoryId categoryId, CedarGroupId groupId, CategoryPermission permission) {
    String cypher = CypherQueryBuilderCategoryPermission.removePermissionForCategoryFromGroup(permission);
    CypherParameters params = AbstractCypherParamBuilder.matchGroupIdAndCategoryId(groupId, categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing permission");
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
