package org.metadatacenter.util;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.report.FolderServerArtifactReport;

import java.util.List;
import java.util.Map;

public class TrustedByUtil {

  public static void decorateWithTrustedBy(FolderServerResourceExtract resourceExtract,
                                           List<FolderServerResourceExtract> pathInfo,
                                           Map<String, String> folderIdsToEntitiesMap) {
    decorateWithTrustedBy(resourceExtract, extractParentFolderIdFromPathInfo(pathInfo), folderIdsToEntitiesMap);
  }

  public static void decorateWithTrustedBy(FolderServerArtifactReport artifactReport,
                                           Map<String, String> folderIdsToEntitiesMap) {
    String parentFolderId = extractParentFolderIdFromPathInfo(artifactReport.getPathInfo());
    artifactReport.setTrustedBy(generateTrustedBy(parentFolderId, artifactReport.getType(), folderIdsToEntitiesMap));
  }

  public static void decorateWithTrustedBy(FolderServerResourceExtract resourceExtract, String parentFolderId,
                                           Map<String, String> folderIdsToEntitiesMap) {
    if (!resourceExtract.getType().equals(CedarResourceType.FOLDER)) { // cast exception when using folders
      ((FolderServerArtifactExtract) resourceExtract).
          setTrustedBy(generateTrustedBy(parentFolderId, resourceExtract.getType(), folderIdsToEntitiesMap));
    }
  }

  public static void decorateWithTrustedBy(FolderServerResourceExtract resourceExtract, FolderServerFolder parentFolder,
                                           Map<String, String> folderIdsToEntitiesMap) {
    decorateWithTrustedBy(resourceExtract, parentFolder.getId(), folderIdsToEntitiesMap);
  }

  private static String generateTrustedBy(String parentFolderId, CedarResourceType resourceType,
                                          Map<String, String> folderIdsToEntitiesMap) {
    if (resourceType.equals(CedarResourceType.TEMPLATE) ||
        resourceType.equals(CedarResourceType.ELEMENT) ||
        resourceType.equals(CedarResourceType.FIELD) ||
        resourceType.equals(CedarResourceType.INSTANCE)) {
      if (folderIdsToEntitiesMap.containsKey(parentFolderId)) {
        return folderIdsToEntitiesMap.get(parentFolderId);
      }
    }
    return null;
  }

  private static String extractParentFolderIdFromPathInfo(List<FolderServerResourceExtract> pathInfo) {
    if (pathInfo.isEmpty()) {
      return null;
    }
    if (pathInfo.get(pathInfo.size() - 1).getType().equals(CedarResourceType.FOLDER)) {
      return pathInfo.get(pathInfo.size() - 1).getId();
    } else {
      return pathInfo.get(pathInfo.size() - 2).getId();
    }
  }


}



