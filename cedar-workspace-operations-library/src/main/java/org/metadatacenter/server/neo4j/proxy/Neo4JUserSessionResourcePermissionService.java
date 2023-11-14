package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithEverybodyPermission;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarGroupExtract;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    FileSystemResource node = proxies.filesystemResource().findResourceById(resourceId);
    if (node != null) {
      FolderServerUser owner = getFilesystemResourceOwner(resourceId);
      List<FolderServerUser> readUsers = getUsersWithDirectPermission(resourceId, FilesystemResourcePermission.READ);
      List<FolderServerUser> writeUsers = getUsersWithDirectPermission(resourceId, FilesystemResourcePermission.WRITE);
      List<FolderServerGroup> readGroups = getGroupsWithDirectPermission(resourceId, FilesystemResourcePermission.READ);
      List<FolderServerGroup> writeGroups = getGroupsWithDirectPermission(resourceId, FilesystemResourcePermission.WRITE);
      return buildPermissions(owner, readUsers, writeUsers, readGroups, writeGroups);
    } else {
      return null;
    }
  }

  private List<FolderServerUser> getUsersWithDirectPermission(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    return proxies.permission().getUsersWithDirectPermissionOnResource(resourceId, permission);
  }

  private List<FolderServerGroup> getGroupsWithDirectPermission(CedarFilesystemResourceId resourceId, FilesystemResourcePermission permission) {
    return proxies.permission().getGroupsWithDirectPermissionOnResource(resourceId, permission);
  }

  @Override
  public BackendCallResult updateResourcePermissions(CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request) {

    ResourcePermissionRequestValidator prv = new ResourcePermissionRequestValidator(this, proxies, resourceId, request);
    BackendCallResult bcr = prv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CedarNodePermissionsWithExtract currentPermissions = getResourcePermissions(resourceId);
      CedarNodePermissionsWithExtract newPermissions = prv.getPermissions();

      CedarUserId oldOwnerId = currentPermissions.getOwner().getResourceId();
      CedarUserId newOwnerId = newPermissions.getOwner().getResourceId();
      if (oldOwnerId != null && !oldOwnerId.equals(newOwnerId)) {
        proxies.resource().updateResourceOwner(resourceId, newOwnerId);
      }

      Set<ResourcePermissionUserPermissionPair> oldUserPermissions = new HashSet<>();
      for (CedarNodeUserPermission up : currentPermissions.getUserPermissions()) {
        oldUserPermissions.add(up.getAsUserIdPermissionPair());
      }
      Set<ResourcePermissionUserPermissionPair> newUserPermissions = new HashSet<>();
      for (CedarNodeUserPermission up : newPermissions.getUserPermissions()) {
        newUserPermissions.add(up.getAsUserIdPermissionPair());
      }

      Set<ResourcePermissionUserPermissionPair> toRemoveUserPermissions = new HashSet<>(oldUserPermissions);
      toRemoveUserPermissions.removeAll(newUserPermissions);
      if (!toRemoveUserPermissions.isEmpty()) {
        removeUserPermissions(resourceId, toRemoveUserPermissions);
      }

      Set<ResourcePermissionUserPermissionPair> toAddUserPermissions = new HashSet<>(newUserPermissions);
      toAddUserPermissions.removeAll(oldUserPermissions);
      if (!toAddUserPermissions.isEmpty()) {
        addUserPermissions(resourceId, toAddUserPermissions);
      }

      Set<ResourcePermissionGroupPermissionPair> oldGroupPermissions = new HashSet<>();
      for (CedarNodeGroupPermission gp : currentPermissions.getGroupPermissions()) {
        oldGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }
      Set<ResourcePermissionGroupPermissionPair> newGroupPermissions = new HashSet<>();
      for (CedarNodeGroupPermission gp : newPermissions.getGroupPermissions()) {
        newGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }

      Set<ResourcePermissionGroupPermissionPair> toRemoveGroupPermissions = new HashSet<>(oldGroupPermissions);
      toRemoveGroupPermissions.removeAll(newGroupPermissions);
      if (!toRemoveGroupPermissions.isEmpty()) {
        removeGroupPermissions(resourceId, toRemoveGroupPermissions);
      }

      Set<ResourcePermissionGroupPermissionPair> toAddGroupPermissions = new HashSet<>(newGroupPermissions);
      toAddGroupPermissions.removeAll(oldGroupPermissions);
      if (!toAddGroupPermissions.isEmpty()) {
        addGroupPermissions(resourceId, toAddGroupPermissions);
      }

      ResourceWithEverybodyPermission node = proxies.filesystemResource().findResourceById(resourceId);
      if (node != null) {
        FolderServerGroup everybody = proxies.group().getEverybodyGroup();
        NodeSharePermission setEverybodyPermission = null;
        for (ResourcePermissionGroupPermissionPair groupPermission : newGroupPermissions) {
          if (groupPermission.getGroup().getId().equals(everybody.getId())) {
            NodeSharePermission everybodyPermissionCandidate = NodeSharePermission.fromGroupPermission(groupPermission);
            if (everybodyPermissionCandidate != node.getEverybodyPermission()) {
              setEverybodyPermission = everybodyPermissionCandidate;
            }
          }
        }
        if (setEverybodyPermission == null && node.getEverybodyPermission() != NodeSharePermission.NONE) {
          setEverybodyPermission = NodeSharePermission.NONE;
        }

        if (setEverybodyPermission != null) {
          proxies.resource().setEverybodyPermission(resourceId, setEverybodyPermission);
        }
      }

      return new BackendCallResult();
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

  private CedarNodePermissionsWithExtract buildPermissions(FolderServerUser owner, List<FolderServerUser> readUsers,
                                                           List<FolderServerUser> writeUsers,
                                                           List<FolderServerGroup> readGroups, List<FolderServerGroup> writeGroups) {
    CedarNodePermissionsWithExtract permissions = new CedarNodePermissionsWithExtract();
    CedarUserExtract o = owner.buildExtract();
    permissions.setOwner(o);
    if (readUsers != null) {
      for (FolderServerUser user : readUsers) {
        CedarUserExtract u = user.buildExtract();
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, FilesystemResourcePermission.READ);
        permissions.addUserPermissions(up);
      }
    }
    if (writeUsers != null) {
      for (FolderServerUser user : writeUsers) {
        CedarUserExtract u = user.buildExtract();
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, FilesystemResourcePermission.WRITE);
        permissions.addUserPermissions(up);
      }
    }
    if (readGroups != null) {
      for (FolderServerGroup group : readGroups) {
        CedarGroupExtract g = group.buildExtract();
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, FilesystemResourcePermission.READ);
        permissions.addGroupPermissions(gp);
      }
    }
    if (writeGroups != null) {
      for (FolderServerGroup group : writeGroups) {
        CedarGroupExtract g = group.buildExtract();
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, FilesystemResourcePermission.WRITE);
        permissions.addGroupPermissions(gp);
      }
    }
    return permissions;
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

  private void addGroupPermissions(CedarFilesystemResourceId resourceId, Set<ResourcePermissionGroupPermissionPair> toAddGroupPermissions) {
    for (ResourcePermissionGroupPermissionPair pair : toAddGroupPermissions) {
      proxies.permission().addPermissionToGroup(resourceId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

  private void removeGroupPermissions(CedarFilesystemResourceId resourceId, Set<ResourcePermissionGroupPermissionPair> toRemoveGroupPermissions) {
    for (ResourcePermissionGroupPermissionPair pair : toRemoveGroupPermissions) {
      proxies.permission().removePermissionFromGroup(resourceId, pair.getGroup().getResourceId(), pair.getPermission());
    }
  }

  private void addUserPermissions(CedarFilesystemResourceId resourceId, Set<ResourcePermissionUserPermissionPair> toAddUserPermissions) {
    for (ResourcePermissionUserPermissionPair pair : toAddUserPermissions) {
      proxies.permission().addPermissionToUser(resourceId, pair.getUser().getResourceIds(), pair.getPermission());
    }
  }

  private void removeUserPermissions(CedarFilesystemResourceId resourceId, Set<ResourcePermissionUserPermissionPair> toRemoveUserPermissions) {
    for (ResourcePermissionUserPermissionPair pair : toRemoveUserPermissions) {
      proxies.permission().removePermissionFromUser(resourceId, pair.getUser().getResourceIds(), pair.getPermission());
    }
  }

}
