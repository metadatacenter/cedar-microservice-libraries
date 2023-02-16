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
    if (permissionSession.userHasWriteAccessToResource(id)) {
      currentUserResourcePermissions.setCanWrite(true);
      currentUserResourcePermissions.setCanDelete(true);
      currentUserResourcePermissions.setCanRead(true);
      currentUserResourcePermissions.setCanShare(true);
    } else if (permissionSession.userHasReadAccessToResource(id)) {
      currentUserResourcePermissions.setCanRead(true);
    }
    if (permissionSession.userCanChangeOwnerOfResource(id)) {
      currentUserResourcePermissions.setCanChangeOwner(true);
    }
    OutcomeWithReason versioningOutcome = versionSession.userCanPerformVersioning(resource);
    if (versioningOutcome.isNegative()) {
      currentUserResourcePermissions.setCreateDraftErrorKey(versioningOutcome.getReason());
      currentUserResourcePermissions.setPublishErrorKey(versioningOutcome.getReason());
    } else {
      OutcomeWithReason publishOutcome = versionSession.resourceCanBePublished(resource);
      if (publishOutcome.isPositive()) {
        currentUserResourcePermissions.setCanPublish(true);
      } else {
        currentUserResourcePermissions.setPublishErrorKey(publishOutcome.getReason());
      }
      OutcomeWithReason createDraftOutcome = versionSession.resourceCanBeDrafted(resource);
      if (createDraftOutcome.isPositive()) {
        currentUserResourcePermissions.setCanCreateDraft(true);
      } else {
        currentUserResourcePermissions.setCreateDraftErrorKey(createDraftOutcome.getReason());
      }
    }
    if (resource.getType() == CedarResourceType.TEMPLATE) {
      currentUserResourcePermissions.setCanPopulate(true);
    }
    if (resource.getType() == CedarResourceType.INSTANCE) {
      InstanceArtifactWithIsBasedOn instance = (InstanceArtifactWithIsBasedOn) resource;
      CedarTemplateId basedOnTemplate = instance.getIsBasedOn();
      if (basedOnTemplate != null) {
        String basedOnTemplateId = basedOnTemplate.getId();
        if (isSubmittable(basedOnTemplateId)) {
          currentUserResourcePermissions.setCanSubmit(true);
        }
      }
    }
    currentUserResourcePermissions.setCanCopy(true);
    if (resource instanceof ResourceWithOpenFlag) {
      ResourceWithOpenFlag res = (ResourceWithOpenFlag) resource;
      currentUserResourcePermissions.setCanMakeOpen(!res.isOpen());
      currentUserResourcePermissions.setCanMakeNotOpen(res.isOpen());
    } else {
      currentUserResourcePermissions.setCanMakeOpen(false);
      currentUserResourcePermissions.setCanMakeNotOpen(false);
    }
  }

  private boolean isSubmittable(String basedOnTemplateId) {
    return cedarConfig.getSubmissionConfig().getSubmittableTemplateIds() != null && cedarConfig.getSubmissionConfig().getSubmittableTemplateIds().contains(basedOnTemplateId);
  }

}
