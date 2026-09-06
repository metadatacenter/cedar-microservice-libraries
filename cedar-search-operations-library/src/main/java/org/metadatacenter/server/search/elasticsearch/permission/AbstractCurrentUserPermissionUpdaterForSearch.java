package org.metadatacenter.server.search.elasticsearch.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.permission.currentuserpermission.CurrentUserPermissionUpdater;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceAccessContext;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapabilityPolicy;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;
import java.util.Set;

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

  protected ResourceAuthority resourceAuthority() {
    boolean owner = documentIsOwned();
    if (owner) {
      return new ResourceAuthority(null, true);
    }
    ResourceRole role = indexedRole();
    return new ResourceAuthority(role, false);
  }

  protected Set<ResourceCapability> resourceCapabilities(ResourceAuthority authority) {
    FolderServerNodeInfo info = indexedDocument.getInfo();
    boolean protectedFolder = info.getType() == CedarResourceType.FOLDER
        && (info.getIsRoot() || info.getIsSystem() || info.getIsUserHome());
    return ResourceCapabilityPolicy.evaluate(authority,
        new ResourceAccessContext(info.getType(), protectedFolder), cedarUser);
  }

  private ResourceRole indexedRole() {
    ResourceRole role = null;
    if (containsRole(indexedDocument.getUsers(), ResourceRole.MANAGER) || containsLegacyPermission("write")) {
      role = ResourceRole.MANAGER;
    } else if (containsRole(indexedDocument.getUsers(), ResourceRole.EDITOR)) {
      role = ResourceRole.EDITOR;
    } else if (containsRole(indexedDocument.getUsers(), ResourceRole.VIEWER) || containsLegacyPermission("read")) {
      role = ResourceRole.VIEWER;
    }
    NodeSharePermission everyone = indexedDocument.getComputedEverybodyPermission();
    if (everyone == NodeSharePermission.WRITE) {
      role = ResourceRole.strongest(role, ResourceRole.MANAGER);
    } else if (everyone == NodeSharePermission.READ) {
      role = ResourceRole.strongest(role, ResourceRole.VIEWER);
    }
    return role;
  }

  private boolean containsRole(List<String> users, ResourceRole role) {
    return containsKey(users, CedarNodeMaterializedPermissions.getKey(cedarUser.getId(), role));
  }

  private boolean containsLegacyPermission(String permission) {
    return containsKey(indexedDocument.getUsers(), cedarUser.getId() + "|" + permission);
  }

  private static boolean containsKey(List<String> users, String lookup) {
    if (users == null) {
      return false;
    }
    for (String pair : users) {
      if (lookup.equals(pair)) {
        return true;
      }
    }
    return false;
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
    } else if (!Boolean.TRUE.equals(indexedDocument.getInfo().isLatestVersion())) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }
    return OutcomeWithReason.positive();
  }

  public OutcomeWithReason resourceCanBeDrafted() {
    if (indexedDocument.getInfo().getPublicationStatus() != BiboStatus.PUBLISHED) {
      return OutcomeWithReason.negative(CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED);
    } else if (!Boolean.TRUE.equals(indexedDocument.getInfo().isLatestVersion())) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }
    return OutcomeWithReason.positive();
  }

}
