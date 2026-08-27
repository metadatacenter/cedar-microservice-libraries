package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.ResourceIdEverybodyPermissionTuple;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderResource;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Neo4JProxyResourcePermission extends AbstractNeo4JProxy {

  Neo4JProxyResourcePermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  boolean updatePermissionsAtomically(CedarFilesystemResourceId resourceId, CedarUserId newOwnerId,
                                      Set<ResourcePermissionUserPermissionPair> removeUserPermissions,
                                      Set<ResourcePermissionUserPermissionPair> addUserPermissions,
                                      Set<ResourcePermissionGroupPermissionPair> removeGroupPermissions,
                                      Set<ResourcePermissionGroupPermissionPair> addGroupPermissions,
                                      NodeSharePermission everybodyPermission) {
    List<CypherQuery> changes = new ArrayList<>();

    if (newOwnerId != null) {
      changes.add(removeOwnerQuery(resourceId));
      changes.add(setOwnerQuery(resourceId, newOwnerId));
    }
    for (ResourcePermissionUserPermissionPair pair : removeUserPermissions) {
      changes.add(removePermissionQuery(resourceId, pair.getUser().getResourceIds(), pair.getPermission()));
    }
    for (ResourcePermissionUserPermissionPair pair : addUserPermissions) {
      changes.add(addPermissionQuery(resourceId, pair.getUser().getResourceIds(), pair.getPermission()));
    }
    for (ResourcePermissionGroupPermissionPair pair : removeGroupPermissions) {
      changes.add(removePermissionQuery(resourceId, pair.getGroup().getResourceId(), pair.getPermission()));
    }
    for (ResourcePermissionGroupPermissionPair pair : addGroupPermissions) {
      changes.add(addPermissionQuery(resourceId, pair.getGroup().getResourceId(), pair.getPermission()));
    }
    if (everybodyPermission != null) {
      changes.add(setEverybodyPermissionQuery(resourceId, everybodyPermission));
    }

    return executeWriteBatch(changes, "updating resource permissions");
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

  private CypherQuery removeOwnerQuery(CedarFilesystemResourceId resourceId) {
    return new CypherQueryWithParameters(CypherQueryBuilderResource.removeResourceOwner(),
        CypherParamBuilderResource.matchId(resourceId));
  }

  private CypherQuery setOwnerQuery(CedarFilesystemResourceId resourceId, CedarUserId userId) {
    return new CypherQueryWithParameters(CypherQueryBuilderResource.setResourceOwner(),
        CypherParamBuilderResource.matchResourceAndUser(resourceId, userId));
  }

  private CypherQuery setEverybodyPermissionQuery(CedarFilesystemResourceId resourceId,
                                                  NodeSharePermission everybodyPermission) {
    return new CypherQueryWithParameters(CypherQueryBuilderFilesystemResource.setEverybodyPermission(),
        CypherParamBuilderFilesystemResource.matchResourceIdAndEverybodyPermission(resourceId, everybodyPermission));
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
