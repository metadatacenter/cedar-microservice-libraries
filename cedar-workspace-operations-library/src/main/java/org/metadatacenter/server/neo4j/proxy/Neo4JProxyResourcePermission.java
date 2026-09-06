package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.folderserver.ResourceIdEverybodyPermissionTuple;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedResourcePermissions;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.auth.CedarNodeGroupPermission;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsWithExtract;
import org.metadatacenter.server.security.model.auth.CedarNodeUserPermission;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.types.Node;

import java.util.ArrayList;
import java.util.List;

public class Neo4JProxyResourcePermission extends AbstractNeo4JProxy {

  Neo4JProxyResourcePermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  VersionedResourcePermissions getVersionedPermissions(CedarFilesystemResourceId resourceId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.getVersionedPermissions(),
        CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId));
    return executeInReadTransaction(tx -> readVersionedPermissions(run(tx, query)),
        "reading versioned resource permissions");
  }

  VersionedResourcePermissions replacePermissions(CedarFilesystemResourceId resourceId,
                                                   CedarNodePermissionsWithExtract requested,
                                                   RevisionPrecondition precondition) {
    CedarUserId ownerId = requested.getOwner().getResourceId();
    List<String> userIds = new ArrayList<>();
    List<String> viewerUserIds = new ArrayList<>();
    List<String> editorUserIds = new ArrayList<>();
    List<String> managerUserIds = new ArrayList<>();
    for (CedarNodeUserPermission grant : requested.getUserPermissions()) {
      String id = grant.getUser().getId();
      userIds.add(id);
      switch (grant.getRole()) {
        case VIEWER -> viewerUserIds.add(id);
        case EDITOR -> editorUserIds.add(id);
        case MANAGER -> managerUserIds.add(id);
      }
    }

    List<String> groupIds = new ArrayList<>();
    List<String> viewerGroupIds = new ArrayList<>();
    List<String> editorGroupIds = new ArrayList<>();
    List<String> managerGroupIds = new ArrayList<>();
    NodeSharePermission everybodyPermission = NodeSharePermission.NONE;
    FolderServerGroup everybody = proxies.group().getEverybodyGroup();
    for (CedarNodeGroupPermission grant : requested.getGroupPermissions()) {
      String id = grant.getGroup().getId();
      groupIds.add(id);
      switch (grant.getRole()) {
        case VIEWER -> viewerGroupIds.add(id);
        case EDITOR -> editorGroupIds.add(id);
        case MANAGER -> managerGroupIds.add(id);
      }
      if (everybody != null && everybody.getId().equals(id)) {
        everybodyPermission = grant.getRole() == ResourceRole.VIEWER
            ? NodeSharePermission.READ : NodeSharePermission.NONE;
      }
    }

    NodeSharePermission finalEverybodyPermission = everybodyPermission;
    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderFilesystemResourcePermission.lockPermissions(),
          CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId));
      Result lockResult = run(tx, lock);
      if (!lockResult.hasNext()) {
        return null;
      }
      long currentRevision = lockResult.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }

      CypherQueryWithParameters replace = new CypherQueryWithParameters(
          CypherQueryBuilderFilesystemResourcePermission.replacePermissions(),
          CypherParamBuilderFilesystemResource.replacePermissions(resourceId, ownerId,
              userIds, viewerUserIds, editorUserIds, managerUserIds,
              groupIds, viewerGroupIds, editorGroupIds, managerGroupIds,
              finalEverybodyPermission, currentRevision));
      return readVersionedPermissions(run(tx, replace));
    }, "replacing versioned resource permissions");
  }

  VersionedResourcePermissions transferOwnership(CedarFilesystemResourceId resourceId,
                                                  CedarUserId currentOwnerId,
                                                  CedarUserId newOwnerId,
                                                  RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderFilesystemResourcePermission.lockPermissions(),
          CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId));
      Result lockResult = run(tx, lock);
      if (!lockResult.hasNext()) {
        return null;
      }
      long currentRevision = lockResult.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, newOwnerId);
      params.put(org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder.OWNER_ID, currentOwnerId);
      params.put(org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder.CURRENT_REVISION, currentRevision);
      CypherQueryWithParameters transfer = new CypherQueryWithParameters(
          CypherQueryBuilderFilesystemResourcePermission.transferOwnership(), params);
      return readVersionedPermissions(run(tx, transfer));
    }, "transferring resource ownership");
  }

  private Result run(Transaction tx, CypherQueryWithParameters query) {
    return tx.run(query.getRunnableQuery(), query.getParameterMap());
  }

  private VersionedResourcePermissions readVersionedPermissions(Result result) {
    CedarNodePermissionsWithExtract permissions = new CedarNodePermissionsWithExtract();
    long revision = -1;
    while (result.hasNext()) {
      Record record = result.next();
      revision = record.get("revision").asLong();
      if (permissions.getOwner() == null && !record.get("owner").isNull()) {
        FolderServerUser owner = buildNode(record.get("owner").asNode(), FolderServerUser.class);
        permissions.setOwner(owner.buildExtract());
      }
      if (!record.get("principal").isNull()) {
        String relationName = record.get("permission").asString();
        RelationLabel relation = RelationLabel.forValue(relationName);
        ResourceRole role = relation == null ? null : relation.getResourceRole();
        if (role == null) {
          throw new IllegalStateException("Unexpected resource role relation: " + relationName);
        }
        if ("user".equals(record.get("principalType").asString())) {
          FolderServerUser user = buildNode(record.get("principal").asNode(), FolderServerUser.class);
          permissions.addUserPermissions(new CedarNodeUserPermission(user.buildExtract(), role));
        } else {
          FolderServerGroup group = buildNode(record.get("principal").asNode(), FolderServerGroup.class);
          permissions.addGroupPermissions(new CedarNodeGroupPermission(group.buildExtract(), role));
        }
      }
    }
    return revision < 0 ? null : new VersionedResourcePermissions(permissions, revision);
  }

  private <T extends CedarResource> T buildNode(Node node, Class<T> clazz) {
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return buildClass(json, clazz);
  }

  boolean addRole(CedarFilesystemResourceId resourceId, CedarGroupId groupId, ResourceRole role) {
    return executeWrite(addRoleQuery(resourceId, groupId, role), "adding resource role");
  }

  boolean removeRole(CedarFilesystemResourceId resourceId, CedarGroupId groupId, ResourceRole role) {
    return executeWrite(removeRoleQuery(resourceId, groupId, role), "removing resource role");
  }

  boolean addRole(CedarFilesystemResourceId resourceId, CedarUserId userId, ResourceRole role) {
    return executeWrite(addRoleQuery(resourceId, userId, role), "adding resource role");
  }

  boolean removeRole(CedarFilesystemResourceId resourceId, CedarUserId userId, ResourceRole role) {
    return executeWrite(removeRoleQuery(resourceId, userId, role), "removing resource role");
  }

  private CypherQuery addRoleQuery(CedarFilesystemResourceId resourceId, CedarGroupId groupId,
                                   ResourceRole role) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.addRoleToFilesystemResourceForGroup(role),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId));
  }

  private CypherQuery removeRoleQuery(CedarFilesystemResourceId resourceId, CedarGroupId groupId,
                                      ResourceRole role) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.removeRoleForFilesystemResourceFromGroup(role),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId));
  }

  private CypherQuery addRoleQuery(CedarFilesystemResourceId resourceId, CedarUserId userId,
                                   ResourceRole role) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.addRoleToFilesystemResourceForUser(role),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId));
  }

  private CypherQuery removeRoleQuery(CedarFilesystemResourceId resourceId, CedarUserId userId,
                                      ResourceRole role) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.removeRoleForFilesystemResourceFromUser(role),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId));
  }

  void addRoleToUser(CedarFilesystemResourceId resourceId, CedarUserId userId, ResourceRole role) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        addRole(resourceId, userId, role);
      }
    }
  }

  void removeRoleFromUser(CedarFilesystemResourceId resourceId, CedarUserId userId, ResourceRole role) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        removeRole(resourceId, userId, role);
      }
    }
  }

  void addRoleToGroup(CedarFilesystemResourceId resourceId, CedarGroupId groupId, ResourceRole role) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        proxies.permission().addRole(resourceId, groupId, role);
      }
    }
  }

  void removeRoleFromGroup(CedarFilesystemResourceId resourceId, CedarGroupId groupId, ResourceRole role) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        proxies.permission().removeRole(resourceId, groupId, role);
      }
    }
  }

  boolean userHasRoleOnFilesystemResource(CedarUserId userId, CedarFilesystemResourceId resourceId,
                                          ResourceRole requiredRole) {
    String cypher = switch (requiredRole) {
      case VIEWER -> CypherQueryBuilderFilesystemResourcePermission.userHasViewerRoleOnFilesystemResource();
      case EDITOR -> CypherQueryBuilderFilesystemResourcePermission.userHasEditorRoleOnFilesystemResource();
      case MANAGER -> CypherQueryBuilderFilesystemResourcePermission.userHasManagerRoleOnFilesystemResource();
    };
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser cedarFSUser = executeReadGetOne(q, FolderServerUser.class);
    return cedarFSUser != null;
  }

  List<FolderServerUser> getUsersWithDirectRoleOnResource(CedarFilesystemResourceId resourceId, ResourceRole role) {
    RelationLabel relationLabel = RelationLabel.forResourceRole(role);
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getUsersWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  List<FolderServerGroup> getGroupsWithDirectRoleOnResource(CedarFilesystemResourceId resourceId, ResourceRole role) {
    RelationLabel relationLabel = RelationLabel.forResourceRole(role);
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getGroupsWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  List<CedarUserId> getUserIdsWithTransitiveRoleOnResource(CedarFilesystemResourceId resourceId, ResourceRole role) {
    String cypher = switch (role) {
      case VIEWER -> CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveViewerRoleOnFilesystemResource();
      case EDITOR -> CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveEditorRoleOnFilesystemResource();
      case MANAGER -> CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveManagerRoleOnFilesystemResource();
    };

    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarUserId.class);
  }

  List<CedarGroupId> getGroupIdsWithTransitiveRoleOnResource(CedarFilesystemResourceId resourceId, ResourceRole role) {
    String cypher = switch (role) {
      case VIEWER -> CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveViewerRoleOnFilesystemResource();
      case EDITOR -> CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveEditorRoleOnFilesystemResource();
      case MANAGER -> CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveManagerRoleOnFilesystemResource();
    };

    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarGroupId.class);
  }

  public NodeSharePermission getTransitiveEverybodyPermission(CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getTransitiveEverybodyPermission();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    List<ResourceIdEverybodyPermissionTuple> nodesWithEverybodyPermission = executeReadGetToupleList(q, ResourceIdEverybodyPermissionTuple.class);
    NodeSharePermission perm = null;
    for (ResourceIdEverybodyPermissionTuple t : nodesWithEverybodyPermission) {
      if (perm == null) {
        perm = t.getEverybodyPermission();
      } else if (t.getEverybodyPermission() == NodeSharePermission.WRITE) {
        perm = NodeSharePermission.WRITE;
      }
    }
    return perm;
  }
}
