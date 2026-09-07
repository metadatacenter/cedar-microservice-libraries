package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.SubmissionConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAction;
import org.metadatacenter.server.security.model.permission.resource.ResourceActionPolicy;
import org.metadatacenter.server.security.model.user.CedarUser;

public class CurrentUserPermissionUpdaterForSearchResource extends AbstractCurrentUserPermissionUpdaterForSearch {

  private CurrentUserPermissionUpdaterForSearchResource(IndexedDocumentDocument indexedDocument, CedarUser cedarUser, CedarConfig cedarConfig) {
    super(indexedDocument, cedarUser, cedarConfig);
  }

  public static CurrentUserPermissionUpdater get(IndexedDocumentDocument indexedDocument, CedarUser cedarUser, CedarConfig cedarConfig) {
    return new CurrentUserPermissionUpdaterForSearchResource(indexedDocument, cedarUser, cedarConfig);
  }

  @Override
  public void update(CurrentUserResourcePermissions currentUserResourcePermissions) {
    ResourceAuthority authority = resourceAuthority();
    currentUserResourcePermissions.applyAccess(authority, resourceCapabilities(authority));
    Boolean open = indexedDocument.getInfo().getIsOpen();
    currentUserResourcePermissions.setAvailableActions(ResourceActionPolicy.evaluate(
        indexedDocument.getInfo().getType(), currentUserResourcePermissions.getCapabilities(), open));

    OutcomeWithReason versioningOutcome = userCanPerformVersioning();
    if (versioningOutcome.isNegative()) {
      currentUserResourcePermissions.setCreateDraftErrorKey(versioningOutcome.getReason());
      currentUserResourcePermissions.setPublishErrorKey(versioningOutcome.getReason());
    } else {
      OutcomeWithReason publishOutcome = resourceCanBePublished();
      if (publishOutcome.isPositive()) {
        currentUserResourcePermissions.setActionAvailable(ResourceAction.PUBLISH, true);
      } else {
        currentUserResourcePermissions.setPublishErrorKey(publishOutcome.getReason());
      }
      OutcomeWithReason createDraftOutcome = resourceCanBeDrafted();
      if (createDraftOutcome.isPositive()) {
        currentUserResourcePermissions.setActionAvailable(ResourceAction.CREATE_DRAFT, true);
      } else {
        currentUserResourcePermissions.setCreateDraftErrorKey(createDraftOutcome.getReason());
      }
    }

    if (indexedDocument.getInfo().getType() == CedarResourceType.INSTANCE) {
      if (isSubmittable()) {
        currentUserResourcePermissions.setActionAvailable(ResourceAction.SUBMIT, true);
      }
    }

  }

  private boolean isSubmittable() {
    CedarTemplateId basedOnTemplate = indexedDocument.getInfo().getIsBasedOnId();
    if (basedOnTemplate != null) {
      String basedOnTemplateId = basedOnTemplate.getId();
      SubmissionConfig submissionConfig = cedarConfig.getSubmissionConfig();
      return submissionConfig != null && submissionConfig.getSubmittableTemplateIds() != null
          && submissionConfig.getSubmittableTemplateIds().contains(basedOnTemplateId);
    }
    return false;
  }
}
