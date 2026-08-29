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
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
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
    List<String> readUserIds = new ArrayList<>();
    List<String> writeUserIds = new ArrayList<>();
    List<String> changeOwnerUserIds = new ArrayList<>();
    List<String> changePermissionsUserIds = new ArrayList<>();
    List<String> publishUserIds = new ArrayList<>();
    List<String> createDraftUserIds = new ArrayList<>();
    for (CedarNodeUserPermission permission : requested.getUserPermissions()) {
      String id = permission.getUser().getId();
      userIds.add(id);
      switch (permission.getPermission()) {
        case READ -> readUserIds.add(id);
        case WRITE -> writeUserIds.add(id);
        case CHANGEOWNER -> changeOwnerUserIds.add(id);
        case CHANGEPERMISSIONS -> changePermissionsUserIds.add(id);
        case PUBLISH -> publishUserIds.add(id);
        case CREATE_DRAFT -> createDraftUserIds.add(id);
      }
    }

    List<String> groupIds = new ArrayList<>();
    List<String> readGroupIds = new ArrayList<>();
    List<String> writeGroupIds = new ArrayList<>();
    List<String> changeOwnerGroupIds = new ArrayList<>();
    List<String> changePermissionsGroupIds = new ArrayList<>();
    List<String> publishGroupIds = new ArrayList<>();
    List<String> createDraftGroupIds = new ArrayList<>();
    NodeSharePermission everybodyPermission = NodeSharePermission.NONE;
    FolderServerGroup everybody = proxies.group().getEverybodyGroup();
    for (CedarNodeGroupPermission permission : requested.getGroupPermissions()) {
      String id = permission.getGroup().getId();
      groupIds.add(id);
      switch (permission.getPermission()) {
        case READ -> readGroupIds.add(id);
        case WRITE -> writeGroupIds.add(id);
        case CHANGEOWNER -> changeOwnerGroupIds.add(id);
        case CHANGEPERMISSIONS -> changePermissionsGroupIds.add(id);
        case PUBLISH -> publishGroupIds.add(id);
        case CREATE_DRAFT -> createDraftGroupIds.add(id);
      }
      if (everybody != null && everybody.getId().equals(id)) {
        everybodyPermission = permission.getPermission() == FilesystemResourcePermission.WRITE
            ? NodeSharePermission.WRITE : NodeSharePermission.READ;
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
              userIds, readUserIds, writeUserIds, changeOwnerUserIds, changePermissionsUserIds,
              publishUserIds, createDraftUserIds, groupIds, readGroupIds, writeGroupIds,
              changeOwnerGroupIds, changePermissionsGroupIds, publishGroupIds, createDraftGroupIds,
              finalEverybodyPermission, currentRevision));
      return readVersionedPermissions(run(tx, replace));
    }, "replacing versioned resource permissions");
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
        String permissionName = record.get("permission").asString();
        RelationLabel relation = RelationLabel.forValue(permissionName);
        FilesystemResourcePermission permission = relation == null ? null : relation.getFilesystemResourcePermission();
        if (permission == null) {
          throw new IllegalStateException("Unexpected resource permission relation: " + permissionName);
        }
        if ("user".equals(record.get("principalType").asString())) {
          FolderServerUser user = buildNode(record.get("principal").asNode(), FolderServerUser.class);
          permissions.addUserPermissions(new CedarNodeUserPermission(user.buildExtract(), permission));
        } else {
          FolderServerGroup group = buildNode(record.get("principal").asNode(), FolderServerGroup.class);
          permissions.addGroupPermissions(new CedarNodeGroupPermission(group.buildExtract(), permission));
        }
      }
    }
    return revision < 0 ? null : new VersionedResourcePermissions(permissions, revision);
  }

  private <T extends CedarResource> T buildNode(Node node, Class<T> clazz) {
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return buildClass(json, clazz);
  }

  boolean addPermission(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    return executeWrite(addPermissionQuery(resourceId, groupId, permission), "adding permission");
  }

  boolean removePermission(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    return executeWrite(removePermissionQuery(resourceId, groupId, permission), "removing permission");
  }

  boolean addPermission(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    return executeWrite(addPermissionQuery(resourceId, userId, permission), "adding permission");
  }

  boolean removePermission(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    return executeWrite(removePermissionQuery(resourceId, userId, permission), "removing permission");
  }

  private CypherQuery addPermissionQuery(CedarFilesystemResourceId resourceId, CedarGroupId groupId,
                                         FilesystemResourcePermission permission) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.addPermissionToFilesystemResourceForGroup(permission),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId));
  }

  private CypherQuery removePermissionQuery(CedarFilesystemResourceId resourceId, CedarGroupId groupId,
                                            FilesystemResourcePermission permission) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.removePermissionForFilesystemResourceFromGroup(permission),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId));
  }

  private CypherQuery addPermissionQuery(CedarFilesystemResourceId resourceId, CedarUserId userId,
                                         FilesystemResourcePermission permission) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.addPermissionToFilesystemResourceForUser(permission),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId));
  }

  private CypherQuery removePermissionQuery(CedarFilesystemResourceId resourceId, CedarUserId userId,
                                            FilesystemResourcePermission permission) {
    return new CypherQueryWithParameters(
        CypherQueryBuilderFilesystemResourcePermission.removePermissionForFilesystemResourceFromUser(permission),
        CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId));
  }

  void addPermissionToUser(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        addPermission(resourceId, userId, permission);
      }
    }
  }

  void removePermissionFromUser(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    FolderServerUser user = proxies.user().findUserById(userId);
    if (user != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        removePermission(resourceId, userId, permission);
      }
    }
  }

  void addPermissionToGroup(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        proxies.permission().addPermission(resourceId, groupId, permission);
      }
    }
  }

  void removePermissionFromGroup(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    FolderServerGroup group = proxies.group().findGroupById(groupId);
    if (group != null) {
      FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        proxies.permission().removePermission(resourceId, groupId, permission);
      }
    }
  }

  boolean userHasReadAccessToFilesystemResource(CedarUserId userId, CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.userCanReadFilesystemResource();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser cedarFSUser = executeReadGetOne(q, FolderServerUser.class);
    return cedarFSUser != null;
  }

  boolean userHasWriteAccessToFilesystemResource(CedarUserId userId, CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.userCanWriteFilesystemResource();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser cedarFSUser = executeReadGetOne(q, FolderServerUser.class);
    return cedarFSUser != null;
  }

  List<FolderServerUser> getUsersWithDirectPermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    RelationLabel relationLabel = switch (permission) {
      case READ -> RelationLabel.CANREAD;
      case WRITE -> RelationLabel.CANWRITE;
      default -> null;
    };
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getUsersWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  List<FolderServerGroup> getGroupsWithDirectPermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    RelationLabel relationLabel = switch (permission) {
      case READ -> RelationLabel.CANREAD;
      case WRITE -> RelationLabel.CANWRITE;
      default -> null;
    };
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getGroupsWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  List<CedarUserId> getUserIdsWithTransitivePermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    String cypher = switch (permission) {
      case READ -> CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveReadOnFilesystemResource();
      case WRITE -> CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveWriteOnFilesystemResource();
      default -> null;
    };

    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarUserId.class);
  }

  List<CedarGroupId> getGroupIdsWithTransitivePermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    String cypher = switch (permission) {
      case READ -> CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveReadOnFilesystemResource();
      case WRITE -> CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveWriteOnFilesystemResource();
      default -> null;
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
