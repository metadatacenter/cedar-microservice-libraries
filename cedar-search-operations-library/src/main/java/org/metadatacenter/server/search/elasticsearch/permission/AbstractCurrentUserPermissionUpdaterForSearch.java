package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;

public abstract class AbstractCurrentUserPermissionUpdaterForSearch extends CurrentUserPermissionUpdater {

  protected final IndexedDocumentDocument indexedDocument;
  protected final CedarUser cedarUser;
  protected final CedarConfig cedarConfig;


  protected AbstractCurrentUserPermissionUpdaterForSearch(IndexedDocumentDocument indexedDocument,
                                                          CedarUser cedarUser, CedarConfig cedarConfig) {
    this.indexedDocument = indexedDocument;
    this.cedarUser = cedarUser;
    this.cedarConfig = cedarConfig;
  }

  protected boolean userCanWrite() {
    if (cedarUser.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE)) {
      return true;
    }
    return containsPermissions(indexedDocument.getUsers(), FilesystemResourcePermission.WRITE);
  }

  protected boolean userCanRead() {
    if (cedarUser.has(CedarPermission.READ_NOT_READABLE_NODE)) {
      return true;
    }
    return containsPermissions(indexedDocument.getUsers(), FilesystemResourcePermission.READ);
  }

  protected boolean containsPermissions(List<String> users, FilesystemResourcePermission permission) {
    //TODO: Optimize this, use map instead
    String lookup = CedarNodeMaterializedPermissions.getKey(cedarUser.getId(), permission);
    for (String pair : users) {
      if (pair.equals(lookup)) {
        return true;
      }
    }
    return false;
  }

  protected boolean userCanChangeOwnerOfFolder() {
    if (cedarUser.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE)) {
      return true;
    } else {
      return documentIsOwned();
    }
  }

  private boolean documentIsOwned() {
    return indexedDocument.getInfo().getOwnedBy() != null &&
        indexedDocument.getInfo().getOwnedBy().equals(cedarUser.getId());
  }

  protected OutcomeWithReason userCanPerformVersioning() {
    if (!documentIsOwned()) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_BY_OWNER);
    }
    if (!indexedDocument.getInfo().getType().isVersioned()) {
      return OutcomeWithReason.negative(CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE);
    }
    return OutcomeWithReason.positive();
  }

  public OutcomeWithReason resourceCanBePublished() {
    if (indexedDocument.getInfo().getPublicationStatus() != BiboStatus.DRAFT) {
      return OutcomeWithReason.negative(CedarErrorKey.PUBLISH_ONLY_DRAFT);
    } else if (!indexedDocument.getInfo().isLatestVersion()) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }
    return OutcomeWithReason.positive();
  }

  public OutcomeWithReason resourceCanBeDrafted() {
    if (indexedDocument.getInfo().getPublicationStatus() != BiboStatus.PUBLISHED) {
      return OutcomeWithReason.negative(CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED);
    } else if (!indexedDocument.getInfo().isLatestVersion()) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }
    return OutcomeWithReason.positive();
  }

}
