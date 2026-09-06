package org.metadatacenter.server.permissions;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithOpenFlag;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.security.model.InstanceArtifactWithIsBasedOn;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAction;
import org.metadatacenter.server.security.model.permission.resource.ResourceActionPolicy;

public class CurrentUserPermissionUpdaterForGraphDbResource extends CurrentUserPermissionUpdater {

  private final ResourcePermissionServiceSession permissionSession;
  private final VersionServiceSession versionSession;
  private final CedarConfig cedarConfig;
  private final FilesystemResourceWithCurrentUserPermissions resource;

  private CurrentUserPermissionUpdaterForGraphDbResource(ResourcePermissionServiceSession permissionSession, VersionServiceSession versionSession,
                                                         CedarConfig cedarConfig, FilesystemResourceWithCurrentUserPermissions resource) {
    this.permissionSession = permissionSession;
    this.versionSession = versionSession;
    this.cedarConfig = cedarConfig;
    this.resource = resource;
  }

  public static CurrentUserPermissionUpdater get(ResourcePermissionServiceSession permissionSession, VersionServiceSession versionSession,
                                                 CedarConfig cedarConfig, FilesystemResourceWithCurrentUserPermissions resource) {
    return new CurrentUserPermissionUpdaterForGraphDbResource(permissionSession, versionSession, cedarConfig, resource);
  }

  @Override
  public void update(CurrentUserResourcePermissions currentUserResourcePermissions){
    CedarFilesystemResourceId id = resource.getResourceId();
    ResourceAuthority authority = permissionSession.getResourceAuthority(id);
    currentUserResourcePermissions.applyAccess(authority, permissionSession.getResourceCapabilities(id));
    Boolean open = resource instanceof ResourceWithOpenFlag resourceWithOpenFlag
        ? resourceWithOpenFlag.isOpen() : null;
    currentUserResourcePermissions.setAvailableActions(ResourceActionPolicy.evaluate(
        resource.getType(), currentUserResourcePermissions.getCapabilities(), open));
    OutcomeWithReason versioningOutcome = versionSession.userCanPerformVersioning(resource);
    if (versioningOutcome.isNegative()) {
      currentUserResourcePermissions.setCreateDraftErrorKey(versioningOutcome.getReason());
      currentUserResourcePermissions.setPublishErrorKey(versioningOutcome.getReason());
    } else {
      OutcomeWithReason publishOutcome = versionSession.resourceCanBePublished(resource);
      if (publishOutcome.isPositive()) {
        currentUserResourcePermissions.setActionAvailable(ResourceAction.PUBLISH, true);
      } else {
        currentUserResourcePermissions.setPublishErrorKey(publishOutcome.getReason());
      }
      OutcomeWithReason createDraftOutcome = versionSession.resourceCanBeDrafted(resource);
      if (createDraftOutcome.isPositive()) {
        currentUserResourcePermissions.setActionAvailable(ResourceAction.CREATE_DRAFT, true);
      } else {
        currentUserResourcePermissions.setCreateDraftErrorKey(createDraftOutcome.getReason());
      }
    }
    if (resource.getType() == CedarResourceType.INSTANCE) {
      InstanceArtifactWithIsBasedOn instance = (InstanceArtifactWithIsBasedOn) resource;
      CedarTemplateId basedOnTemplate = instance.getIsBasedOn();
      if (basedOnTemplate != null) {
        String basedOnTemplateId = basedOnTemplate.getId();
        if (isSubmittable(basedOnTemplateId)) {
          currentUserResourcePermissions.setActionAvailable(ResourceAction.SUBMIT, true);
        }
      }
    }
  }

  private boolean isSubmittable(String basedOnTemplateId) {
    return cedarConfig.getSubmissionConfig().getSubmittableTemplateIds() != null && cedarConfig.getSubmissionConfig().getSubmittableTemplateIds().contains(basedOnTemplateId);
  }

}
