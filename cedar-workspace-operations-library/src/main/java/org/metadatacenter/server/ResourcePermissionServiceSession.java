package org.metadatacenter.server;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarNodePermissionsWithExtract;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

import java.util.Set;

public interface ResourcePermissionServiceSession {

  CedarNodePermissionsWithExtract getResourcePermissions(CedarFilesystemResourceId resourceId);

  VersionedResourcePermissions getVersionedResourcePermissions(CedarFilesystemResourceId resourceId);

  CedarNodeMaterializedPermissions getResourceMaterializedPermission(CedarFilesystemResourceId resourceId);

  /**
   * The same, for a caller that has already resolved the resource.
   *
   * <p>Resolving it is a graph query, and the identifier form runs it again for the one field it reads
   * off the result. The callers that matter -- the index rebuild and the permission cascade, which run
   * this once per resource over the whole repository -- are iterating resources they already hold, so
   * the second lookup is pure repetition on the only paths where its cost is multiplied.
   */
  CedarNodeMaterializedPermissions getResourceMaterializedPermission(FileSystemResource resource);

  default BackendCallResult<VersionedResourcePermissions> updateResourcePermissions(
      CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request) {
    return updateResourcePermissions(resourceId, request, RevisionPrecondition.any());
  }

  default BackendCallResult<VersionedResourcePermissions> updateResourcePermissions(
      CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request, RevisionPrecondition precondition) {
    return updateResourcePermissions(resourceId, request, precondition, GraphWrite.NONE);
  }

  /**
   * Replaces the resource's permissions and runs {@code alongside} in the same graph transaction, after
   * the replacement and only when it succeeds, so that the two commit together or not at all.
   */
  BackendCallResult<VersionedResourcePermissions> updateResourcePermissions(
      CedarFilesystemResourceId resourceId, ResourcePermissionsRequest request, RevisionPrecondition precondition,
      GraphWrite alongside);

  default BackendCallResult<VersionedResourcePermissions> transferResourceOwnership(
      CedarFilesystemResourceId resourceId, CedarUserId newOwnerId, RevisionPrecondition precondition) {
    return transferResourceOwnership(resourceId, newOwnerId, precondition, GraphWrite.NONE);
  }

  /** Transfers the resource's ownership and runs {@code alongside} in the same graph transaction. */
  BackendCallResult<VersionedResourcePermissions> transferResourceOwnership(
      CedarFilesystemResourceId resourceId, CedarUserId newOwnerId, RevisionPrecondition precondition,
      GraphWrite alongside);

  ResourceAuthority getResourceAuthority(CedarFilesystemResourceId resourceId);

  Set<ResourceCapability> getResourceCapabilities(CedarFilesystemResourceId resourceId);

  boolean userHasRole(CedarFilesystemResourceId resourceId, ResourceRole requiredRole);

  boolean userHasCapability(CedarFilesystemResourceId resourceId, ResourceCapability capability);

  boolean userIsOwnerOfResource(CedarFilesystemResourceId resource);

  boolean userHasPermission(CedarPermission permission);
}
