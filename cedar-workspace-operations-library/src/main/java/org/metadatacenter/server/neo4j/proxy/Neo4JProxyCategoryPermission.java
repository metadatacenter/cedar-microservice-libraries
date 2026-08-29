package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedCategoryPermissions;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategoryPermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryGroupPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryUserPermission;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.types.Node;

import java.util.ArrayList;
import java.util.List;

public class Neo4JProxyCategoryPermission extends AbstractNeo4JProxy {

  Neo4JProxyCategoryPermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  VersionedCategoryPermissions getVersionedPermissions(CedarCategoryId categoryId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderCategoryPermission.getVersionedPermissions(),
        CypherParamBuilderCategory.matchCategory(categoryId));
    return executeInReadTransaction(tx -> readVersionedPermissions(run(tx, query)),
        "reading versioned category permissions");
  }

  VersionedCategoryPermissions replacePermissions(CedarCategoryId categoryId, CategoryPermissions requested,
                                                   RevisionPrecondition precondition) {
    CedarUserId ownerId = requested.getOwner().getResourceId();
    List<String> userIds = new ArrayList<>();
    List<String> attachUserIds = new ArrayList<>();
    List<String> writeUserIds = new ArrayList<>();
    for (CategoryUserPermission permission : requested.getUserPermissions()) {
      String id = permission.getUser().getId();
      userIds.add(id);
      if (permission.getPermission() == CategoryPermission.ATTACH) {
        attachUserIds.add(id);
      } else {
        writeUserIds.add(id);
      }
    }

    List<String> groupIds = new ArrayList<>();
    List<String> attachGroupIds = new ArrayList<>();
    List<String> writeGroupIds = new ArrayList<>();
    for (CategoryGroupPermission permission : requested.getGroupPermissions()) {
      String id = permission.getGroup().getId();
      groupIds.add(id);
      if (permission.getPermission() == CategoryPermission.ATTACH) {
        attachGroupIds.add(id);
      } else {
        writeGroupIds.add(id);
      }
    }

    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderCategoryPermission.lockPermissions(),
          CypherParamBuilderCategory.matchCategory(categoryId));
      Result lockResult = run(tx, lock);
      if (!lockResult.hasNext()) {
        return null;
      }
      long currentRevision = lockResult.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }

      CypherQueryWithParameters replace = new CypherQueryWithParameters(
          CypherQueryBuilderCategoryPermission.replacePermissions(),
          CypherParamBuilderCategory.replacePermissions(categoryId, ownerId, userIds, attachUserIds,
              writeUserIds, groupIds, attachGroupIds, writeGroupIds, currentRevision));
      return readVersionedPermissions(run(tx, replace));
    }, "replacing versioned category permissions");
  }

  private Result run(Transaction tx, CypherQueryWithParameters query) {
    return tx.run(query.getRunnableQuery(), query.getParameterMap());
  }

  private VersionedCategoryPermissions readVersionedPermissions(Result result) {
    CategoryPermissions permissions = new CategoryPermissions();
    long revision = -1;
    while (result.hasNext()) {
      Record record = result.next();
      revision = record.get("revision").asLong();
      if (permissions.getOwner() == null && !record.get("owner").isNull()) {
        FolderServerUser owner = buildNode(record.get("owner").asNode(), FolderServerUser.class);
        permissions.setOwner(owner.buildExtract());
      }
      if (!record.get("principal").isNull()) {
        CategoryPermission permission = switch (record.get("permission").asString()) {
          case "CANATTACHCATEGORY" -> CategoryPermission.ATTACH;
          case "CANWRITECATEGORY" -> CategoryPermission.WRITE;
          default -> throw new IllegalStateException("Unexpected category permission relation");
        };
        if ("user".equals(record.get("principalType").asString())) {
          FolderServerUser user = buildNode(record.get("principal").asNode(), FolderServerUser.class);
          permissions.addUserPermissions(new CategoryUserPermission(user.buildExtract(), permission));
        } else {
          FolderServerGroup group = buildNode(record.get("principal").asNode(), FolderServerGroup.class);
          permissions.addGroupPermissions(new CategoryGroupPermission(group.buildExtract(), permission));
        }
      }
    }
    return revision < 0 ? null : new VersionedCategoryPermissions(permissions, revision);
  }

  private <T extends CedarResource> T buildNode(Node node, Class<T> clazz) {
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return buildClass(json, clazz);
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
      case WRITE -> RelationLabel.CANWRITECATEGORY;
    };
    String cypher = CypherQueryBuilderCategoryPermission.getGroupsWithDirectPermissionOnCategory(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

}
