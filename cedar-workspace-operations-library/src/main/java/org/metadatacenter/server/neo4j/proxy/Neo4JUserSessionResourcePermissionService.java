package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedResourcePermissions;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;

public class Neo4JUserSessionResourcePermissionService extends AbstractNeo4JUserSession implements ResourcePermissionServiceSession {

  private Neo4JUserSessionResourcePermissionService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId,
                                                    String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static ResourcePermissionServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId,
                                                     String localRequestId) {
    return new Neo4JUserSessionResourcePermissionService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public CedarNodePermissionsWithExtract getResourcePermissions(CedarFilesystemResourceId resourceId) {
    VersionedResourcePermissions versioned = getVersionedResourcePermissions(resourceId);
    return versioned == null ? null : versioned.content();
  }

  @Override
  public VersionedResourcePermissions getVersionedResourcePermissions(CedarFilesystemResourceId resourceId) {
    return proxies.permission().getVersionedPermissions(resourceId);
  }

  @Override
  public BackendCallResult<VersionedResourcePermissions> updateResourcePermissions(
      CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request, RevisionPrecondition precondition) {

    ResourcePermissionRequestValidator prv = new ResourcePermissionRequestValidator(this, proxies, resourceId, request);
    BackendCallResult<VersionedResourcePermissions> bcr = prv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CedarNodePermissionsWithExtract newPermissions = prv.getPermissions();
      VersionedResourcePermissions updated = proxies.permission().replacePermissions(resourceId, newPermissions, precondition);
      if (updated == null) {
        BackendCallResult<VersionedResourcePermissions> failure = new BackendCallResult<>();
        failure.addError(org.metadatacenter.error.CedarErrorType.SERVER_ERROR)
            .message("The resource permissions could not be updated");
        return failure;
      }
      bcr.setPayload(updated);
      return bcr;
    }
  }

  @Override
  public boolean userCanChangeOwnerOfResource(CedarFilesystemResourceId resourceId) {
    if (cu.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE)) {
      return true;
    } else {
      FolderServerUser owner = getFilesystemResourceOwner(resourceId);
      return owner != null && owner.getId().equals(cu.getId());
    }
  }

  @Override
  public boolean userHasReadAccessToResource(CedarFilesystemResourceId resourceId) {
    if (cu.has(CedarPermission.READ_NOT_READABLE_NODE)) {
      return true;
    } else {
      return proxies.permission().userHasReadAccessToFilesystemResource(cu.getResourceId(), resourceId)
          || proxies.permission().userHasWriteAccessToFilesystemResource(cu.getResourceId(), resourceId);
    }
  }

  @Override
  public boolean userHasWriteAccessToResource(CedarFilesystemResourceId resourceId) {
    if (cu.has(CedarPermission.WRITE_NOT_WRITABLE_NODE)) {
      return true;
    } else {
      return proxies.permission().userHasWriteAccessToFilesystemResource(cu.getResourceId(), resourceId);
    }
  }

  @Override
  public boolean userIsOwnerOfResource(CedarFilesystemResourceId resourceId) {
    FolderServerUser owner = getFilesystemResourceOwner(resourceId);
    return owner != null && owner.getId().equals(cu.getId());
  }

  @Override
  public CedarNodeMaterializedPermissions getResourceMaterializedPermission(CedarFilesystemResourceId resourceId) {
    FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
    if (node != null) {
      NodeSharePermission everybodyPermission = node.getEverybodyPermission();
      if (everybodyPermission == null) {
        everybodyPermission = proxies.permission().getTransitiveEverybodyPermission(resourceId);
      }

      if (everybodyPermission == null) {
        everybodyPermission = NodeSharePermission.NONE;
      }

      List<CedarUserId> readUsers = new ArrayList<>();
      List<CedarUserId> writeUsers = new ArrayList<>();
      List<CedarGroupId> readGroups = new ArrayList<>();
      List<CedarGroupId> writeGroups = new ArrayList<>();

      if (everybodyPermission == NodeSharePermission.WRITE) {
        // do not read permissions, since everybody will have full access
      } else if (everybodyPermission == NodeSharePermission.READ) {
        // read just write permissions, since everybody can read
        writeUsers = getUserIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.WRITE);
        writeGroups = getGroupIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.WRITE);
      } else {
        // read all permissions, since there is no everybody permission
        writeUsers = getUserIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.WRITE);
        writeGroups = getGroupIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.WRITE);
        readUsers = getUserIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.READ);
        readGroups = getGroupIdsWithTransitivePermission(resourceId, FilesystemResourcePermission.READ);
      }

      return buildMaterializedPermissions(resourceId, readUsers, writeUsers, readGroups, writeGroups, everybodyPermission);
    } else {
      return null;
    }
  }

  private CedarNodeMaterializedPermissions buildMaterializedPermissions(CedarFilesystemResourceId resourceId, List<CedarUserId> readUsers,
                                                                        List<CedarUserId> writeUsers, List<CedarGroupId> readGroups,
                                                                        List<CedarGroupId> writeGroups, NodeSharePermission everybodyPermission) {
    CedarNodeMaterializedPermissions permissions = new CedarNodeMaterializedPermissions(resourceId, everybodyPermission);
    if (readUsers != null) {
      for (CedarUserId userId : readUsers) {
        permissions.setUserPermission(userId.getId(), FilesystemResourcePermission.READ);
      }
    }
    if (writeUsers != null) {
      for (CedarUserId userId : writeUsers) {
        permissions.setUserPermission(userId.getId(), FilesystemResourcePermission.WRITE);
      }
    }
    if (readGroups != null) {
      for (CedarGroupId groupId : readGroups) {
        permissions.setGroupPermission(groupId.getId(), FilesystemResourcePermission.READ);
      }
    }
    if (writeGroups != null) {
      for (CedarGroupId groupId : writeGroups) {
        permissions.setGroupPermission(groupId.getId(), FilesystemResourcePermission.WRITE);
      }
    }
    return permissions;
  }

  private List<CedarUserId> getUserIdsWithTransitivePermission(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    return proxies.permission().getUserIdsWithTransitivePermissionOnResource(resourceId, permission);
  }

  private List<CedarGroupId> getGroupIdsWithTransitivePermission(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    return proxies.permission().getGroupIdsWithTransitivePermissionOnResource(resourceId, permission);
  }

}
