package org.metadatacenter.server.cache.user;

import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithUsersAndUserNamesData;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.report.FolderServerArtifactReport;
import org.metadatacenter.model.folderserver.report.FolderServerInstanceReport;
import org.metadatacenter.model.response.FolderServerCategoryListResponse;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.security.model.user.CedarUserSummary;

public final class ProvenanceNameUtil {

  private ProvenanceNameUtil() {
  }

  public static void addProvenanceDisplayName(ResourceWithUsersAndUserNamesData resource) {
    if (resource != null) {
      CedarUserSummary creator = UserSummaryCache.getInstance().getUser(resource.getCreatedBy());
      CedarUserSummary updater = UserSummaryCache.getInstance().getUser(resource.getLastUpdatedBy());
      CedarUserSummary owner = UserSummaryCache.getInstance().getUser(resource.getOwnedBy());
      if (creator != null) {
        resource.setCreatedByUserName(creator.getScreenName());
      }
      if (updater != null) {
        resource.setLastUpdatedByUserName(updater.getScreenName());
      }
      if (owner != null) {
        resource.setOwnedByUserName(owner.getScreenName());
      }
      if (resource instanceof FileSystemResource res) {
        for (FolderServerResourceExtract pi : res.getPathInfo()) {
          addProvenanceDisplayName(pi);
        }
      }
    }
  }

  public static void addProvenanceDisplayName(FolderServerResourceExtract resource) {
    if (resource != null) {
      CedarUserSummary creator = UserSummaryCache.getInstance().getUser(resource.getCreatedBy());
      CedarUserSummary updater = UserSummaryCache.getInstance().getUser(resource.getLastUpdatedBy());
      CedarUserSummary owner = UserSummaryCache.getInstance().getUser(resource.getOwnedBy());
      if (creator != null) {
        resource.setCreatedByUserName(creator.getScreenName());
      }
      if (updater != null) {
        resource.setLastUpdatedByUserName(updater.getScreenName());
      }
      if (owner != null) {
        resource.setOwnedByUserName(owner.getScreenName());
      }
    }
  }

  public static void addProvenanceDisplayNames(FolderServerArtifactReport report) {
    for (FolderServerResourceExtract v : report.getVersions()) {
      addProvenanceDisplayName(v);
    }
    for (FolderServerResourceExtract pi : report.getPathInfo()) {
      addProvenanceDisplayName(pi);
    }
    addProvenanceDisplayName(report.getDerivedFromExtract());
    if (report instanceof FolderServerInstanceReport instanceReport) {
      addProvenanceDisplayName(instanceReport.getIsBasedOnExtract());
    }
  }

  public static void addProvenanceDisplayNames(FolderServerNodeListResponse nodeList) {
    for (FolderServerResourceExtract r : nodeList.getResources()) {
      addProvenanceDisplayName(r);
    }
    if (nodeList.getPathInfo() != null) {
      for (FolderServerResourceExtract pi : nodeList.getPathInfo()) {
        addProvenanceDisplayName(pi);
      }
    }
  }

  public static void addProvenanceDisplayNames(FolderServerCategoryListResponse categoryList) {
    for (FolderServerCategory c : categoryList.getCategories()) {
      addProvenanceDisplayName(c);
    }
  }
}
