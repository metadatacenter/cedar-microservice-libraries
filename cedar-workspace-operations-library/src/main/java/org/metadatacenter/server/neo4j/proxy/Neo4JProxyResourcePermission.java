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
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

import java.util.List;

public class Neo4JProxyResourcePermission extends AbstractNeo4JProxy {

  Neo4JProxyResourcePermission(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  boolean addPermission(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.addPermissionToFilesystemResourceForGroup(permission);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding permission");
  }

  boolean removePermission(CedarFilesystemResourceId resourceId, CedarGroupId groupId, FilesystemResourcePermission permission) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.removePermissionForFilesystemResourceFromGroup(permission);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndGroup(resourceId, groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing permission");
  }

  boolean addPermission(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.addPermissionToFilesystemResourceForUser(permission);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding permission");
  }

  boolean removePermission(CedarFilesystemResourceId resourceId, CedarUserId userId, FilesystemResourcePermission permission) {
    String cypher = CypherQueryBuilderFilesystemResourcePermission.removePermissionForFilesystemResourceFromUser(permission);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing permission");
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
    RelationLabel relationLabel = null;
    switch (permission) {
      case READ:
        relationLabel = RelationLabel.CANREAD;
        break;
      case WRITE:
        relationLabel = RelationLabel.CANWRITE;
        break;
    }
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getUsersWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  List<FolderServerGroup> getGroupsWithDirectPermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    RelationLabel relationLabel = null;
    switch (permission) {
      case READ:
        relationLabel = RelationLabel.CANREAD;
        break;
      case WRITE:
        relationLabel = RelationLabel.CANWRITE;
        break;
    }
    String cypher = CypherQueryBuilderFilesystemResourcePermission.getGroupsWithDirectPermissionOnFilesystemResource(relationLabel);
    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  List<CedarUserId> getUserIdsWithTransitivePermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    String cypher = null;
    switch (permission) {
      case READ:
        cypher = CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveReadOnFilesystemResource();
        break;
      case WRITE:
        cypher = CypherQueryBuilderFilesystemResourcePermission.getUserIdsWithTransitiveWriteOnFilesystemResource();
        break;
    }

    CypherParameters params = CypherParamBuilderFilesystemResource.matchFilesystemResource(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarUserId.class);
  }

  List<CedarGroupId> getGroupIdsWithTransitivePermissionOnResource(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    String cypher = null;
    switch (permission) {
      case READ:
        cypher = CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveReadOnFilesystemResource();
        break;
      case WRITE:
        cypher = CypherQueryBuilderFilesystemResourcePermission.getGroupIdsWithTransitiveWriteOnFilesystemResource();
        break;
    }

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
