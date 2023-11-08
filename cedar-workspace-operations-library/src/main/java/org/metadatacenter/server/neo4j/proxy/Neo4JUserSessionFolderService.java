package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.util.CedarUserNameUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Neo4JUserSessionFolderService extends AbstractNeo4JUserSession implements FolderServiceSession {

  private Neo4JUserSessionFolderService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu,
                                        String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static FolderServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser,
                                         String globalRequestId, String localRequestId) {
    return new Neo4JUserSessionFolderService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public FolderServerArtifact createResourceAsChildOfId(FolderServerArtifact newResource,
                                                        CedarFolderId parentFolderId) {
    newResource.setCreatedByTotal(cu.getResourceId());
    return proxies.artifact().createResourceAsChildOfId(newResource, parentFolderId);
  }

  @Override
  public FolderServerFolder updateFolderById(CedarFolderId folderId, Map<NodeProperty, String> updateFields) {
    return proxies.folder().updateFolderById(folderId, updateFields, cu.getResourceId());
  }

  @Override
  public FolderServerArtifact updateArtifactById(CedarArtifactId artifactId, CedarResourceType resourceType,
                                                 Map<NodeProperty, String> updateFields) {
    return proxies.artifact().updateArtifactById(artifactId, updateFields, cu.getResourceId());
  }

  @Override
  public boolean deleteFolderById(CedarFolderId folderId) {
    return proxies.folder().deleteFolderById(folderId);
  }

  @Override
  public boolean deleteResourceById(CedarArtifactId artifactId) {
    return proxies.artifact().deleteArtifactById(artifactId);
  }

  private void setPaths(FileSystemResource node, List<? extends FileSystemResource> path) {
    node.setPath(getPathString(path));
    node.setParentPath(getParentPathString(path));
  }

  @Override
  public void addPathAndParentId(FolderServerFolder folder) {
    if (folder != null) {
      List<FolderServerFolder> path = findFolderPath(folder);
      if (path != null) {
        setPaths(folder, path);
      }
    }
  }

  @Override
  public void addPathAndParentId(FolderServerArtifact artifact) {
    if (artifact != null) {
      List<FileSystemResource> path = proxies.filesystemResource().findResourcePathById(artifact.getResourceId());
      if (path != null) {
        setPaths(artifact, path);
      }
    }
  }

  private String getParentPathString(List<? extends FileSystemResource> path) {
    List<FileSystemResource> p = new ArrayList<>(path);
    if (path.size() > 0) {
      p.remove(p.size() - 1);
    } else {
      return null;
    }
    return getPathString(p);
  }

  private String getPathString(List<? extends FileSystemResource> path) {
    StringBuilder sb = new StringBuilder();
    boolean addSeparator = false;
    for (FileSystemResource node : path) {
      if (addSeparator) {
        sb.append(proxies.pathUtil.getSeparator());
      }
      if (node instanceof FolderServerFolder) {
        if (!((FolderServerFolder) node).isRoot()) {
          addSeparator = true;
        }
      }
      sb.append(node.getName());
    }
    return sb.length() == 0 ? null : sb.toString();
  }

  @Override
  public String sanitizeName(String name) {
    return proxies.pathUtil.sanitizeName(name);
  }

  @Override
  public long findFolderContentsCount(CedarFolderId folderId, NodeListRequest req) {
    return proxies.resource().findFolderContentsCount(folderId, req.getResourceTypes(), req.getVersion(),
        req.getPublicationStatus(),
        cu.getResourceId());
  }

  @Override
  public long findFolderContentsUnfilteredCount(CedarFolderId folderId) {
    return proxies.resource().findFolderContentsUnfilteredCount(folderId);
  }

  @Override
  public FolderServerArtifact findArtifactById(CedarArtifactId artifactId) {
    return proxies.artifact().findArtifactById(artifactId);
  }

  @Override
  public FolderServerSchemaArtifact findSchemaArtifactById(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().findSchemaArtifactById(artifactId);
  }

  @Override
  public FileSystemResource findResourceById(CedarFilesystemResourceId resourceId) {
    return proxies.filesystemResource().findResourceById(resourceId);
  }

  @Override
  public List<FolderServerResourceExtract> findAllNodes(int limit, int offset, List<String> sortList) {
    return proxies.resource().findAllNodes(limit, offset, sortList);
  }

  @Override
  public long findAllNodesCount() {
    return proxies.filesystemResource().findAllNodesCount();
  }

  @Override
  public List<FileSystemResource> findFolderContentsFiltered(CedarFolderId folderId,
                                                             List<CedarResourceType> resourceTypeList,
                                                             ResourceVersionFilter version,
                                                             ResourcePublicationStatusFilter publicationStatus,
                                                             int limit, int offset, List<String> sortList) {
    return proxies.resource().findFolderContentsFiltered(folderId, resourceTypeList, version, publicationStatus,
        limit, offset, sortList, cu);
  }

  @Override
  public List<FolderServerResourceExtract> findFolderContentsExtract(CedarFolderId folderId, NodeListRequest req) {
    return proxies.resource().findFolderContentsExtract(folderId, req.getResourceTypes(), req.getVersion(),
        req.getPublicationStatus(),
        req.getLimit(), req.getOffset(), req.getSort(), cu.getResourceId());
  }

  @Override
  public List<Map<String, Object>> findFolderContentsExtractMap(CedarFolderId folderId, NodeListRequest req,
                                                                List<String> fieldNameList) {
    return proxies.resource().findFolderContentsExtractMap(folderId, req.getResourceTypes(), req.getVersion(),
        req.getPublicationStatus(),
        req.getLimit(), req.getOffset(), req.getSort(), cu.getResourceId(), fieldNameList);
  }

  @Override
  public String getRootPath() {
    return proxies.pathUtil.getRootPath();
  }

  @Override
  public FolderServerFolder findFolderById(CedarFolderId folderId) {
    return proxies.folder().findFolderById(folderId);
  }

  @Override
  public FolderServerFolder findFolderByPath(String path) {
    return proxies.folder().findFolderByPath(path);
  }

  @Override
  public FileSystemResource findFilesystemResourceByParentFolderIdAndName(CedarFolderId parentFolderId, String name) {
    return proxies.resource().findFilesystemResourceByParentFolderIdAndName(parentFolderId, name);
  }

  @Override
  public FolderServerFolder createFolderAsChildOfId(FolderServerFolder newFolder, CedarFolderId parentFolderId, CedarFolderId newFolderId) {
    newFolder.setCreatedByTotal(cu.getResourceId());
    return proxies.folder().createFolderAsChildOfId(newFolder, parentFolderId, newFolderId);
  }

  @Override
  public boolean moveResource(CedarArtifactId sourceArtifactId, CedarFolderId targetFolderId) {
    return proxies.artifact().moveArtifact(sourceArtifactId, targetFolderId);
  }

  @Override
  public boolean moveFolder(CedarFolderId sourceFolderId, CedarFolderId targetFolderId) {
    return proxies.folder().moveFolder(sourceFolderId, targetFolderId);
  }

  @Override
  public String normalizePath(String path) {
    return proxies.pathUtil.normalizePath(path);
  }

  @Override
  public List<FolderServerFolder> findFolderPath(FolderServerFolder folder) {
    if (folder.isRoot()) {
      List<FolderServerFolder> pathInfo = new ArrayList<>();
      pathInfo.add(folder);
      return pathInfo;
    } else {
      return proxies.folder().findFolderPathById(folder.getResourceId());
    }
  }

  @Override
  public List<FolderServerResourceExtract> findNodePathExtract(FileSystemResource node) {
    if (node instanceof FolderServerFolder && ((FolderServerFolder) node).isRoot()) {
      List<FolderServerResourceExtract> pathInfo = new ArrayList<>();
      pathInfo.add(FolderServerFolderExtract.fromFolder((FolderServerFolder) node));
      return pathInfo;
    } else {
      return proxies.filesystemResource().findFilesystemResourcePathExtractById(node.getResourceId());
    }
  }

  @Override
  public FolderServerFolder ensureUserHomeExists() {
    FolderServerFolder currentUserHomeFolder = findHomeFolderOf();
    if (currentUserHomeFolder == null) {
      currentUserHomeFolder = createUserHomeFolder();
      cu.setHomeFolderId(currentUserHomeFolder.getId());
      proxies.user().updateUser(cu);
    }
    return currentUserHomeFolder;
  }

  @Override
  public List<FolderServerResourceExtract> viewSharedWithMe(List<CedarResourceType> resourceTypeList,
                                                            ResourceVersionFilter version,
                                                            ResourcePublicationStatusFilter publicationStatus,
                                                            int limit, int offset,
                                                            List<String> sortList) {
    return proxies.resource().viewSharedWithMeFiltered(resourceTypeList, version, publicationStatus, limit, offset,
        sortList, cu.getResourceId());
  }

  @Override
  public List<FolderServerResourceExtract> viewSharedWithEverybody(List<CedarResourceType> resourceTypeList,
                                                                   ResourceVersionFilter version,
                                                                   ResourcePublicationStatusFilter publicationStatus,
                                                                   int limit, int offset,
                                                                   List<String> sortList) {
    return proxies.resource().viewSharedWithEverybodyFiltered(resourceTypeList, version, publicationStatus, limit,
        offset, sortList,
        cu.getResourceId());
  }

  @Override
  public long viewSharedWithMeCount(List<CedarResourceType> resourceTypeList, ResourceVersionFilter version,
                                    ResourcePublicationStatusFilter publicationStatus) {
    return proxies.resource().viewSharedWithMeFilteredCount(resourceTypeList, version, publicationStatus,
        cu.getResourceId());
  }

  @Override
  public long viewSharedWithEverybodyCount(List<CedarResourceType> resourceTypeList, ResourceVersionFilter version,
                                           ResourcePublicationStatusFilter publicationStatus) {
    return proxies.resource().viewSharedWithEverybodyFilteredCount(resourceTypeList, version, publicationStatus,
        cu.getResourceId());
  }

  @Override
  public List<FolderServerResourceExtract> viewAll(List<CedarResourceType> resourceTypeList,
                                                   ResourceVersionFilter version,
                                                   ResourcePublicationStatusFilter publicationStatus, int limit,
                                                   int offset, List<String> sortList) {
    return proxies.resource().viewAllFiltered(resourceTypeList, version, publicationStatus, limit, offset, sortList,
        cu);
  }

  @Override
  public long viewAllCount(List<CedarResourceType> resourceTypeList, ResourceVersionFilter version,
                           ResourcePublicationStatusFilter publicationStatus) {
    return proxies.resource().viewAllFilteredCount(resourceTypeList, version, publicationStatus, cu);
  }

  @Override
  public List<FolderServerResourceExtract> viewSpecialFolders(int limit, int offset, List<String> sortList) {
    return proxies.resource().viewSpecialFoldersFiltered(limit, offset, sortList, cu);
  }

  @Override
  public long viewSpecialFoldersCount() {
    return proxies.resource().viewSpecialFoldersFilteredCount(cu);
  }

  @Override
  public List<FileSystemResource> findAllDescendantNodesById(CedarFolderId folderId) {
    return proxies.resource().findAllDescendantResourcesByFolderId(folderId);
  }

  @Override
  public List<FileSystemResource> findAllChildArtifactsOfFolder(CedarFolderId id) {
    return proxies.folder().findAllChildArtifactsOfFolder(id);
  }

  @Override
  public List<FileSystemResource> findAllNodesVisibleByGroupId(CedarGroupId id) {
    return proxies.resource().findAllFilesystemResourcesVisibleByGroupId(id);
  }

  @Override
  public FolderServerFolder findHomeFolderOf() {
    return proxies.folder().findHomeFolderOf(cu.getResourceId());
  }

  @Override
  public FolderServerFolder createUserHomeFolder() {
    CedarUserId userId = cu.getResourceId();
    Neo4jConfig config = proxies.config;
    FolderServerFolder currentUserHomeFolder;
    FolderServerFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
    // usersFolder should not be null at this point. If it is, we let the NPE to be thrown
    String name = CedarUserNameUtil.getDisplayName(cedarConfig, cu);
    String description = CedarUserNameUtil.getHomeFolderDescription(cedarConfig, cu);
    FolderServerFolder newUserHome = new FolderServerFolder();
    newUserHome.setName(name);
    newUserHome.setDescription(description);
    newUserHome.setRoot(false);
    newUserHome.setSystem(false);
    newUserHome.setUserHome(true);
    newUserHome.setCreatedByTotal(userId);
    newUserHome.setHomeOf(userId.getId());
    CedarFolderId newHomeFolderId = linkedDataUtil.buildNewLinkedDataIdObject(CedarFolderId.class);
    currentUserHomeFolder = createFolderAsChildOfId(newUserHome, usersFolder.getResourceId(), newHomeFolderId);
    return currentUserHomeFolder;
  }

  @Override
  public boolean setDerivedFrom(CedarArtifactId newId, CedarArtifactId oldId) {
    return proxies.artifact().setDerivedFrom(newId, oldId);
  }

  @Override
  public boolean unsetLatestVersion(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().unsetLatestVersion(artifactId);
  }

  @Override
  public boolean setLatestVersion(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().setLatestVersion(artifactId);
  }

  @Override
  public boolean setLatestPublishedVersion(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().setLatestPublishedVersion(artifactId);
  }

  @Override
  public boolean unsetLatestPublishedVersion(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().unsetLatestPublishedVersion(artifactId);
  }

  @Override
  public boolean unsetLatestDraftVersion(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().unsetLatestDraftVersion(artifactId);
  }

  @Override
  public boolean setOpen(CedarArtifactId artifactId) {
    return proxies.artifact().setOpen(artifactId);
  }

  @Override
  public boolean setNotOpen(CedarArtifactId artifactId) {
    return proxies.artifact().setNotOpen(artifactId);
  }

  @Override
  public boolean setOpen(CedarFolderId folderId) {
    return proxies.artifact().setOpen(folderId);
  }

  @Override
  public boolean setNotOpen(CedarFolderId folderId) {
    return proxies.artifact().setNotOpen(folderId);
  }

  @Override
  public long getNumberOfInstances(CedarTemplateId templateId) {
    return proxies.artifact().getIsBasedOnCount(templateId);
  }

  @Override
  public FolderServerArtifactExtract findResourceExtractById(CedarArtifactId artifactId) {
    return proxies.artifact().findResourceExtractById(artifactId);
  }

  @Override
  public List<FolderServerArtifactExtract> getVersionHistory(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().getVersionHistory(artifactId);
  }

  @Override
  public List<FolderServerArtifactExtract> getVersionHistoryWithPermission(CedarSchemaArtifactId artifactId) {
    return proxies.artifact().getVersionHistoryWithPermission(artifactId, cu.getResourceId());
  }

  @Override
  public List<FolderServerResourceExtract> searchIsBasedOn(List<CedarResourceType> resourceTypeList,
                                                           CedarTemplateId isBasedOnId, int limit,
                                                           int offset, List<String> sortList) {
    return proxies.resource().searchIsBasedOn(resourceTypeList, isBasedOnId, limit, offset, sortList, cu);
  }

  @Override
  public long searchIsBasedOnCount(List<CedarResourceType> resourceTypeList, CedarTemplateId isBasedOnId) {
    return proxies.resource().searchIsBasedOnCount(resourceTypeList, isBasedOnId, cu);
  }

  @Override
  public CedarResourceType getResourceType(CedarResourceId resourceId) {
    return proxies.resource().getResourceType(resourceId, cu.getResourceId());
  }

  @Override
  public boolean isArtifactOpenImplicitly(CedarArtifactId artifactId) {
    return proxies.resource().isFileSystemResourceOpenImplicitly(artifactId);
  }

  @Override
  public long getFolderCount() {
    return proxies.folder().getFolderCount();
  }

  @Override
  public FolderServerFolder getParentFolder(CedarArtifactId artifactId) {
    return proxies.folder().getParentFolder(artifactId);
  }
}
