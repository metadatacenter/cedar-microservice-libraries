package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedResourcePermissions;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAccessContext;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapabilityPolicy;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
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
  public BackendCallResult<VersionedResourcePermissions> transferResourceOwnership(
      CedarFilesystemResourceId resourceId, CedarUserId newOwnerId, RevisionPrecondition precondition) {
    BackendCallResult<VersionedResourcePermissions> result = new BackendCallResult<>();
    FileSystemResource resource = proxies.filesystemResource().findResourceById(resourceId);
    if (resource == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.NOT_FOUND)
          .errorKey(org.metadatacenter.error.CedarErrorKey.NODE_NOT_FOUND)
          .message("Resource not found")
          .parameter("resourceId", resourceId);
      return result;
    }
    if (!userIsOwnerOfResource(resourceId)) {
      result.addError(org.metadatacenter.error.CedarErrorType.PERMISSION)
          .errorKey(org.metadatacenter.error.CedarErrorKey.NOT_AUTHORIZED)
          .message("Only the current owner may transfer ownership")
          .parameter("resourceId", resourceId);
      return result;
    }
    FolderServerUser newOwner = proxies.user().findUserById(newOwnerId);
    if (newOwner == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.NOT_FOUND)
          .errorKey(org.metadatacenter.error.CedarErrorKey.USER_NOT_FOUND)
          .message("The new owner could not be found")
          .parameter("userId", newOwnerId);
      return result;
    }
    if (cu.getId().equals(newOwner.getId())) {
      result.addError(org.metadatacenter.error.CedarErrorType.INVALID_ARGUMENT)
          .errorKey(org.metadatacenter.error.CedarErrorKey.INVALID_INPUT)
          .message("The new owner must be a different user")
          .parameter("userId", newOwnerId);
      return result;
    }
    VersionedResourcePermissions transferred = proxies.permission().transferOwnership(
        resourceId, cu.getResourceId(), newOwnerId, precondition);
    if (transferred == null) {
      result.addError(org.metadatacenter.error.CedarErrorType.SERVER_ERROR)
          .message("Ownership could not be transferred")
          .parameter("resourceId", resourceId);
      return result;
    }
    result.setPayload(transferred);
    return result;
  }

  @Override
  public ResourceAuthority getResourceAuthority(CedarFilesystemResourceId resourceId) {
    boolean owner = userIsOwnerOfResource(resourceId);
    ResourceRole role = null;
    if (!owner) {
      if (proxies.permission().userHasRoleOnFilesystemResource(cu.getResourceId(), resourceId, ResourceRole.MANAGER)) {
        role = ResourceRole.MANAGER;
      } else if (proxies.permission().userHasRoleOnFilesystemResource(
          cu.getResourceId(), resourceId, ResourceRole.EDITOR)) {
        role = ResourceRole.EDITOR;
      } else if (proxies.permission().userHasRoleOnFilesystemResource(
          cu.getResourceId(), resourceId, ResourceRole.VIEWER)) {
        role = ResourceRole.VIEWER;
      }
    }
    return new ResourceAuthority(role, owner);
  }

  @Override
  public boolean userHasRole(CedarFilesystemResourceId resourceId, ResourceRole requiredRole) {
    return getResourceAuthority(resourceId).satisfies(requiredRole);
  }

  @Override
  public Set<ResourceCapability> getResourceCapabilities(CedarFilesystemResourceId resourceId) {
    FileSystemResource resource = proxies.filesystemResource().findResourceById(resourceId);
    if (resource == null) {
      return Collections.emptySet();
    }
    boolean protectedFolder = resource instanceof FolderServerFolder folder
        && (folder.isRoot() || folder.isSystem() || folder.isUserHome());
    ResourceAccessContext accessContext = new ResourceAccessContext(resource.getType(), protectedFolder);
    return ResourceCapabilityPolicy.evaluate(getResourceAuthority(resourceId), accessContext, cu);
  }

  @Override
  public boolean userHasCapability(CedarFilesystemResourceId resourceId, ResourceCapability capability) {
    return getResourceCapabilities(resourceId).contains(capability);
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

      List<CedarUserId> viewerUsers = new ArrayList<>();
      List<CedarUserId> editorUsers = new ArrayList<>();
      List<CedarUserId> managerUsers = new ArrayList<>();
      List<CedarGroupId> viewerGroups = new ArrayList<>();
      List<CedarGroupId> editorGroups = new ArrayList<>();
      List<CedarGroupId> managerGroups = new ArrayList<>();

      if (everybodyPermission == NodeSharePermission.WRITE) {
        // A legacy exception. Preserve its current reach until the owner-review migration removes it.
      } else if (everybodyPermission == NodeSharePermission.READ) {
        // Viewer is supplied by Everyone; retain only more capable named grants in the index.
      }
      managerUsers = getUserIdsWithTransitiveRole(resourceId, ResourceRole.MANAGER);
      managerGroups = getGroupIdsWithTransitiveRole(resourceId, ResourceRole.MANAGER);
      editorUsers = getUserIdsWithTransitiveRole(resourceId, ResourceRole.EDITOR);
      editorGroups = getGroupIdsWithTransitiveRole(resourceId, ResourceRole.EDITOR);
      if (everybodyPermission == NodeSharePermission.NONE) {
        viewerUsers = getUserIdsWithTransitiveRole(resourceId, ResourceRole.VIEWER);
        viewerGroups = getGroupIdsWithTransitiveRole(resourceId, ResourceRole.VIEWER);
      }

      return buildMaterializedPermissions(resourceId, viewerUsers, editorUsers, managerUsers,
          viewerGroups, editorGroups, managerGroups, everybodyPermission);
    } else {
      return null;
    }
  }

  private CedarNodeMaterializedPermissions buildMaterializedPermissions(CedarFilesystemResourceId resourceId,
                                                                        List<CedarUserId> viewerUsers,
                                                                        List<CedarUserId> editorUsers,
                                                                        List<CedarUserId> managerUsers,
                                                                        List<CedarGroupId> viewerGroups,
                                                                        List<CedarGroupId> editorGroups,
                                                                        List<CedarGroupId> managerGroups,
                                                                        NodeSharePermission everybodyPermission) {
    CedarNodeMaterializedPermissions permissions = new CedarNodeMaterializedPermissions(resourceId, everybodyPermission);
    addUserRoles(permissions, viewerUsers, ResourceRole.VIEWER);
    addUserRoles(permissions, editorUsers, ResourceRole.EDITOR);
    addUserRoles(permissions, managerUsers, ResourceRole.MANAGER);
    addGroupRoles(permissions, viewerGroups, ResourceRole.VIEWER);
    addGroupRoles(permissions, editorGroups, ResourceRole.EDITOR);
    addGroupRoles(permissions, managerGroups, ResourceRole.MANAGER);
    return permissions;
  }

  private void addUserRoles(CedarNodeMaterializedPermissions permissions, List<CedarUserId> users, ResourceRole role) {
    for (CedarUserId userId : users) {
      permissions.setUserRole(userId.getId(), ResourceRole.strongest(permissions.getUserRoles().get(userId.getId()), role));
    }
  }

  private void addGroupRoles(CedarNodeMaterializedPermissions permissions, List<CedarGroupId> groups, ResourceRole role) {
    for (CedarGroupId groupId : groups) {
      permissions.setGroupRole(groupId.getId(), ResourceRole.strongest(permissions.getGroupRoles().get(groupId.getId()), role));
    }
  }

  private List<CedarUserId> getUserIdsWithTransitiveRole(CedarFilesystemResourceId resourceId, ResourceRole role) {
    return proxies.permission().getUserIdsWithTransitiveRoleOnResource(resourceId, role);
  }

  private List<CedarGroupId> getGroupIdsWithTransitiveRole(CedarFilesystemResourceId resourceId, ResourceRole role) {
    return proxies.permission().getGroupIdsWithTransitiveRoleOnResource(resourceId, role);
  }

}
