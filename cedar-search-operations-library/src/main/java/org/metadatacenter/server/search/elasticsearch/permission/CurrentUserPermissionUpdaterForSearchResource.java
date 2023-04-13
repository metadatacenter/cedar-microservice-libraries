package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CurrentUserResourcePermissions;
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
    if (userCanWrite()) {
      currentUserResourcePermissions.setCanWrite(true);
      currentUserResourcePermissions.setCanDelete(true);
      currentUserResourcePermissions.setCanRead(true);
      currentUserResourcePermissions.setCanShare(true);
    } else if (userCanRead()) {
      currentUserResourcePermissions.setCanRead(true);
    }

    if (userCanChangeOwnerOfFolder()) {
      currentUserResourcePermissions.setCanChangeOwner(true);
    }

    currentUserResourcePermissions.setCanCopy(true);

    if (indexedDocument.getInfo().getType() == CedarResourceType.TEMPLATE) {
      currentUserResourcePermissions.setCanPopulate(true);
    }

    OutcomeWithReason versioningOutcome = userCanPerformVersioning();
    if (versioningOutcome.isNegative()) {
      currentUserResourcePermissions.setCreateDraftErrorKey(versioningOutcome.getReason());
      currentUserResourcePermissions.setPublishErrorKey(versioningOutcome.getReason());
    } else {
      OutcomeWithReason publishOutcome = resourceCanBePublished();
      if (publishOutcome.isPositive()) {
        currentUserResourcePermissions.setCanPublish(true);
      } else {
        currentUserResourcePermissions.setPublishErrorKey(publishOutcome.getReason());
      }
      OutcomeWithReason createDraftOutcome = resourceCanBeDrafted();
      if (createDraftOutcome.isPositive()) {
        currentUserResourcePermissions.setCanCreateDraft(true);
      } else {
        currentUserResourcePermissions.setCreateDraftErrorKey(createDraftOutcome.getReason());
      }
    }

    if (indexedDocument.getInfo().getType() == CedarResourceType.INSTANCE) {
      if (isSubmittable()) {
        currentUserResourcePermissions.setCanSubmit(true);
      }
    }

    currentUserResourcePermissions.setCanMakeOpen(userCanWrite() && !indexedDocument.getInfo().getIsOpen());
    currentUserResourcePermissions.setCanMakeNotOpen(userCanWrite() && indexedDocument.getInfo().getIsOpen());
  }

  private boolean isSubmittable() {
    CedarTemplateId basedOnTemplate = indexedDocument.getInfo().getIsBasedOnId();
    if (basedOnTemplate != null) {
      String basedOnTemplateId = basedOnTemplate.getId();
      return cedarConfig.getSubmissionConfig().getSubmittableTemplateIds() != null &&
          cedarConfig.getSubmissionConfig().getSubmittableTemplateIds().contains(basedOnTemplateId);
    }
    return false;
  }
}
