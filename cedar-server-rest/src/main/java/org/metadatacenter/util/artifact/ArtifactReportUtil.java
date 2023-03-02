package org.metadatacenter.util.artifact;

import org.metadatacenter.bridge.GraphDbPermissionReader;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.folderserver.report.FolderServerArtifactReport;
import org.metadatacenter.model.folderserver.report.FolderServerInstanceReport;
import org.metadatacenter.model.folderserver.report.FolderServerSchemaArtifactReport;
import org.metadatacenter.model.folderserver.report.FolderServerTemplateReport;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactReportUtil {

  private ArtifactReportUtil() {
  }

  public static FolderServerArtifactReport getArtifactReport(CedarRequestContext c, CedarConfig cedarConfig, FolderServerArtifact artifact,
                                                             FolderServiceSession folderSession,
                                                             ResourcePermissionServiceSession permissionSession,
                                                             CategoryServiceSession categorySession) {
    FolderServerArtifactReport resourceReport = null;
    if (artifact.getType() == CedarResourceType.INSTANCE) {
      resourceReport = FolderServerInstanceReport.fromResource(artifact);
      decorateResourceWithIsBasedOn(folderSession, permissionSession, (FolderServerInstanceReport) resourceReport);
    } else if (artifact.getType() == CedarResourceType.FIELD) {
      resourceReport = FolderServerSchemaArtifactReport.fromResource(artifact);
      decorateResourceWithVersionHistory(folderSession, (FolderServerSchemaArtifactReport) resourceReport);
    } else if (artifact.getType() == CedarResourceType.ELEMENT) {
      resourceReport = FolderServerSchemaArtifactReport.fromResource(artifact);
      decorateResourceWithVersionHistory(folderSession, (FolderServerSchemaArtifactReport) resourceReport);
    } else if (artifact.getType() == CedarResourceType.TEMPLATE) {
      resourceReport = FolderServerSchemaArtifactReport.fromResource(artifact);
      decorateResourceWithVersionHistory(folderSession, (FolderServerSchemaArtifactReport) resourceReport);
      decorateResourceWithNumberOfInstances(folderSession, (FolderServerTemplateReport)resourceReport);
    }

    decorateResourceWithDerivedFrom(folderSession, permissionSession, resourceReport);
    GraphDbPermissionReader.decorateResourceWithCurrentUserPermissions(c, permissionSession, cedarConfig, resourceReport);

    decorateResourceWithCategories(categorySession, resourceReport);

    return resourceReport;
  }

  private static void decorateResourceWithNumberOfInstances(FolderServiceSession folderSession, FolderServerTemplateReport templateReport) {
    templateReport.setNumberOfInstances(folderSession.getNumberOfInstances((CedarTemplateId) templateReport.getResourceId()));
  }

  private static void decorateResourceWithIsBasedOn(FolderServiceSession folderSession, ResourcePermissionServiceSession permissionServiceSession,
                                                    FolderServerInstanceReport instanceReport) {
    if (instanceReport.getIsBasedOn() != null) {
      FolderServerTemplateExtract resourceExtract =
          (FolderServerTemplateExtract) folderSession.findResourceExtractById(instanceReport.getIsBasedOn());
      if (resourceExtract != null) {
        boolean hasReadAccess = permissionServiceSession.userHasReadAccessToResource(resourceExtract.getResourceId());
        if (hasReadAccess) {
          instanceReport.setIsBasedOnExtract(resourceExtract);
        } else {
          instanceReport.setIsBasedOnExtract(FolderServerResourceExtract.anonymous(resourceExtract));
        }
      }
    }
  }

  private static void decorateResourceWithDerivedFrom(FolderServiceSession folderSession, ResourcePermissionServiceSession permissionServiceSession,
                                                      FolderServerArtifactReport artifactReport) {
    if (artifactReport.getDerivedFrom() != null && artifactReport.getDerivedFrom().getId() != null) {
      FolderServerArtifactExtract resourceExtract = folderSession.findResourceExtractById(artifactReport.getDerivedFrom());
      if (resourceExtract != null) {
        boolean hasReadAccess = permissionServiceSession.userHasReadAccessToResource(resourceExtract.getResourceId());
        if (hasReadAccess) {
          artifactReport.setDerivedFromExtract(resourceExtract);
        } else {
          artifactReport.setDerivedFromExtract(FolderServerResourceExtract.anonymous(resourceExtract));
        }
      }
    }
  }

  private static void decorateResourceWithCategories(CategoryServiceSession serviceSession, FolderServerArtifactReport artifactReport) {
    List<List<FolderServerCategoryExtract>> categories = serviceSession.getAttachedCategoryPaths(artifactReport.getResourceId());
    artifactReport.setCategories(categories);
  }

  private static void decorateResourceWithVersionHistory(FolderServiceSession folderSession, FolderServerSchemaArtifactReport resourceReport) {
    List<FolderServerArtifactExtract> allVersions = folderSession.getVersionHistory(resourceReport.getResourceId());
    List<FolderServerArtifactExtract> allVersionsWithPermission = folderSession.getVersionHistoryWithPermission(resourceReport.getResourceId());
    Map<String, FolderServerArtifactExtract> accessibleMap = new HashMap<>();
    for (FolderServerArtifactExtract e : allVersionsWithPermission) {
      accessibleMap.put(e.getId(), e);
    }

    List<FolderServerArtifactExtract> visibleVersions = new ArrayList<>();
    for (FolderServerArtifactExtract v : allVersions) {
      if (accessibleMap.containsKey(v.getId())) {
        visibleVersions.add(v);
      } else {
        visibleVersions.add(FolderServerResourceExtract.anonymous(v));
      }
    }
    resourceReport.setVersions(visibleVersions);
  }

}
