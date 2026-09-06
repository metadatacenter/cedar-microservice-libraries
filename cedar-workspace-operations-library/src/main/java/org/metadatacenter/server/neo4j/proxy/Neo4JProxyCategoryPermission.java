package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
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
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategoryPermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryGroupPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;
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
    List<String> userIds = new ArrayList<>();
    List<String> viewerUserIds = new ArrayList<>();
    List<String> classifierUserIds = new ArrayList<>();
    List<String> editorUserIds = new ArrayList<>();
    List<String> managerUserIds = new ArrayList<>();
    for (CategoryUserPermission grant : requested.getUserPermissions()) {
      String id = grant.getUser().getId();
      userIds.add(id);
      switch (grant.getRole()) {
        case VIEWER -> viewerUserIds.add(id);
        case CLASSIFIER -> classifierUserIds.add(id);
        case EDITOR -> editorUserIds.add(id);
        case MANAGER -> managerUserIds.add(id);
      }
    }

    List<String> groupIds = new ArrayList<>();
    List<String> viewerGroupIds = new ArrayList<>();
    List<String> classifierGroupIds = new ArrayList<>();
    List<String> editorGroupIds = new ArrayList<>();
    List<String> managerGroupIds = new ArrayList<>();
    for (CategoryGroupPermission grant : requested.getGroupPermissions()) {
      String id = grant.getGroup().getId();
      groupIds.add(id);
      switch (grant.getRole()) {
        case VIEWER -> viewerGroupIds.add(id);
        case CLASSIFIER -> classifierGroupIds.add(id);
        case EDITOR -> editorGroupIds.add(id);
        case MANAGER -> managerGroupIds.add(id);
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
          CypherParamBuilderCategory.replacePermissions(categoryId, userIds, viewerUserIds,
              classifierUserIds, editorUserIds, managerUserIds, groupIds, viewerGroupIds,
              classifierGroupIds, editorGroupIds, managerGroupIds, currentRevision));
      return readVersionedPermissions(run(tx, replace));
    }, "replacing versioned category permissions");
  }

  VersionedCategoryPermissions transferOwnership(CedarCategoryId categoryId, CedarUserId currentOwnerId,
                                                  CedarUserId newOwnerId, RevisionPrecondition precondition) {
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
      CypherQueryWithParameters transfer = new CypherQueryWithParameters(
          CypherQueryBuilderCategoryPermission.transferOwnership(),
          CypherParamBuilderCategory.transferOwnership(categoryId, currentOwnerId, newOwnerId, currentRevision));
      return readVersionedPermissions(run(tx, transfer));
    }, "transferring category ownership");
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
        CategoryRole role = switch (record.get("role").asString()) {
          case "VIEWER_ROLE" -> CategoryRole.VIEWER;
          case "CANATTACHCATEGORY" -> CategoryRole.CLASSIFIER;
          case "EDITOR_ROLE" -> CategoryRole.EDITOR;
          case "CANWRITECATEGORY" -> CategoryRole.MANAGER;
          default -> throw new IllegalStateException("Unexpected category permission relation");
        };
        if ("user".equals(record.get("principalType").asString())) {
          FolderServerUser user = buildNode(record.get("principal").asNode(), FolderServerUser.class);
          permissions.addUserPermissions(new CategoryUserPermission(user.buildExtract(), role));
        } else {
          FolderServerGroup group = buildNode(record.get("principal").asNode(), FolderServerGroup.class);
          permissions.addGroupPermissions(new CategoryGroupPermission(group.buildExtract(), role));
        }
      }
    }
    return revision < 0 ? null : new VersionedCategoryPermissions(permissions, revision);
  }

  private <T extends CedarResource> T buildNode(Node node, Class<T> clazz) {
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return buildClass(json, clazz);
  }

  void addCategoryRoleToUser(CedarCategoryId categoryId, CedarUserId userId, CategoryRole role) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FolderServerCategory category = proxies.category().getCategoryById(categoryId);
      if (category != null) {
        addCategoryRole(categoryId, userId, role);
      }
    }
  }

  boolean ensureViewerRoleForGroup(CedarCategoryId categoryId, CedarGroupId groupId) {
    CypherQuery query = new CypherQueryWithParameters(
        CypherQueryBuilderCategoryPermission.ensureViewerRoleForGroup(),
        AbstractCypherParamBuilder.matchGroupIdAndCategoryId(groupId, categoryId));
    return executeWrite(query, "ensuring category Viewer role");
  }

  private boolean addCategoryRole(CedarCategoryId categoryId, CedarUserId userId, CategoryRole role) {
    return executeWrite(addRoleQuery(categoryId, userId, role), "adding category role");
  }

  private CypherQuery addRoleQuery(CedarCategoryId categoryId, CedarUserId userId, CategoryRole role) {
    return new CypherQueryWithParameters(CypherQueryBuilderCategoryPermission.addRoleToCategoryForUser(role),
        AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId));
  }

  public CategoryAuthority getCategoryAuthority(CedarUserId userId, CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategoryPermission.getAuthority();
    CypherParameters params = AbstractCypherParamBuilder.matchUserIdAndCategoryId(userId, categoryId);
    CypherQueryWithParameters q = new CypherQueryWithParameters(cypher, params);
    return executeInReadTransaction(tx -> {
      Result result = tx.run(q.getRunnableQuery(), q.getParameterMap());
      if (!result.hasNext()) {
        return new CategoryAuthority(null, false);
      }
      Record record = result.next();
      CategoryRole strongest = null;
      for (Object relation : record.get("roleRelations").asList()) {
        strongest = CategoryRole.strongest(strongest, roleForRelation(relation.toString()));
      }
      return new CategoryAuthority(strongest, record.get("owner").asBoolean());
    }, "reading effective category authority");
  }

  private CategoryRole roleForRelation(String relation) {
    return switch (relation) {
      case "VIEWER_ROLE" -> CategoryRole.VIEWER;
      case "CANATTACHCATEGORY" -> CategoryRole.CLASSIFIER;
      case "EDITOR_ROLE" -> CategoryRole.EDITOR;
      case "CANWRITECATEGORY" -> CategoryRole.MANAGER;
      default -> throw new IllegalStateException("Unexpected category role relation " + relation);
    };
  }

}
