package org.metadatacenter.server.cache.user;

import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithUsersAndUserNamesData;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.report.FolderServerArtifactReport;
import org.metadatacenter.model.folderserver.report.FolderServerInstanceReport;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphElement;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphResponse;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphTemplate;
import org.metadatacenter.model.response.FolderServerCategoryListResponse;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.security.model.user.CedarUserSummary;

import java.util.Collection;
import java.util.Map;

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

  public static void addProvenanceDisplayNames(InclusionSubgraphResponse treeResponse) {
    Map<String, InclusionSubgraphElement> elements = treeResponse.getElements();
    Map<String, InclusionSubgraphTemplate> templates = treeResponse.getTemplates();
    addProvenanceDisplayNamesToElements(elements.values());
    addProvenanceDisplayNamesToTemplates(templates.values());
  }

  private static void addProvenanceDisplayNamesToTemplates(Collection<InclusionSubgraphTemplate> templates) {
    if (templates != null) {
      for (InclusionSubgraphTemplate template : templates) {
        addProvenanceDisplayName(template);
      }
    }
  }

  private static void addProvenanceDisplayNamesToElements(Collection<InclusionSubgraphElement> elements) {
    if (elements != null) {
      for (InclusionSubgraphElement element : elements) {
        addProvenanceDisplayName(element);
        if (element.getElements() != null) {
          addProvenanceDisplayNamesToElements(element.getElements().values());
        }
        if (element.getTemplates() != null) {
          addProvenanceDisplayNamesToTemplates(element.getTemplates().values());
        }
      }
    }
  }


}
