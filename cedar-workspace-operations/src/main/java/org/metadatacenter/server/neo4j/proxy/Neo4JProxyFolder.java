package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderUser;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderGroup;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;
import java.util.Map;

public class Neo4JProxyFolder extends AbstractNeo4JProxy {

  Neo4JProxyFolder(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  boolean moveFolder(CedarFolderId sourceFolderId, CedarFolderId targetFolderId) {
    if (sourceFolderId.getId().equals(targetFolderId.getId())) {
      return false;
    }
    if (folderIsAncestorOf(sourceFolderId, targetFolderId)) {
      return false;
    }
    boolean unlink = unlinkFolderFromParent(sourceFolderId);
    if (unlink) {
      return linkFolderUnderFolder(sourceFolderId, targetFolderId);
    }
    return false;
  }

  private boolean folderIsAncestorOf(CedarFolderId parentFolderId, CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.folderIsAncestorOf();
    CypherParameters params = CypherParamBuilderFolder.matchFolderIdAndParentFolderId(folderId, parentFolderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerFolder parent = executeReadGetOne(q, FolderServerFolder.class);
    return parent != null;
  }

  private boolean unlinkFolderFromParent(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.unlinkFolderFromParent();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "unlinking folder");
  }

  private boolean linkFolderUnderFolder(CedarFolderId folderId, CedarFolderId parentFolderId) {
    String cypher = CypherQueryBuilderFolder.linkFolderUnderFolder();
    CypherParameters params = CypherParamBuilderFolder.matchFolderIdAndParentFolderId(folderId, parentFolderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "linking folder");
  }

  FolderServerFolder updateFolderById(CedarFolderId folderId, Map<NodeProperty, String> updateFields,
                                      CedarUserId updatedBy) {
    String cypher = CypherQueryBuilderFolder.updateFolderById(updateFields);
    CypherParameters params = CypherParamBuilderFolder.updateFolderById(folderId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerFolder.class);
  }

  boolean deleteFolderById(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.deleteFolderContentsRecursivelyById();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "deleting folder");
  }

  List<FolderServerFolder> findFolderPathByPath(String path) {
    int cnt = proxies.pathUtil.getPathDepth(path);
    String cypher = CypherQueryBuilderFolder.getFolderLookupQueryByDepth(cnt);
    CypherParameters params = CypherParamBuilderFolder.getFolderLookupByDepthParameters(proxies.pathUtil, path);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerFolder.class);
  }

  private <T extends CedarResource> List<T> findFolderPathGenericById(CedarFolderId id, Class<T> klazz) {
    String cypher = CypherQueryBuilderFolder.getFolderLookupQueryById();
    CypherParameters params = CypherParamBuilderFolder.getFolderLookupByIdParameters(proxies.pathUtil.getRootPath(),
        id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, klazz);
  }

  List<FolderServerFolder> findFolderPathById(CedarFolderId id) {
    return findFolderPathGenericById(id, FolderServerFolder.class);
  }

  FolderServerFolder findFolderByPath(String path) {
    List<FolderServerFolder> folderPath = findFolderPathByPath(path);
    if (folderPath != null && folderPath.size() > 0) {
      return folderPath.get(folderPath.size() - 1);
    }
    return null;
  }

  FolderServerFolder createFolderAsChildOfId(FolderServerFolder newFolder, CedarFolderId parentFolderId) {
    String cypher = CypherQueryBuilderFolder.createFolderAsChildOfId(newFolder);
    CypherParameters params = CypherParamBuilderFolder.createFolder(proxies.getLinkedDataUtil(), newFolder,
        parentFolderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerFolder.class);
  }

  public boolean setOwner(CedarFolderId folderId, CedarUserId userId) {
    String cypher = CypherQueryBuilderFolder.setFolderOwner();
    CypherParameters params = CypherParamBuilderFolder.matchFolderAndUser(folderId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting owner");
  }

  private boolean removeOwner(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.removeFolderOwner();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing owner");
  }

  boolean updateOwner(CedarFolderId folderId, CedarUserId userId) {
    boolean removed = removeOwner(folderId);
    if (removed) {
      return setOwner(folderId, userId);
    }
    return false;
  }

  FolderServerFolder createRootFolder(CedarUserId creatorId) {
    FolderServerFolder newRoot = new FolderServerFolder();
    newRoot.setName(proxies.config.getRootFolderPath());
    newRoot.setDescription(proxies.config.getRootFolderDescription());
    newRoot.setCreatedByTotal(creatorId);
    newRoot.setRoot(true);
    newRoot.setSystem(true);
    newRoot.setUserHome(false);
    String cypher = CypherQueryBuilderFolder.createRootFolder(newRoot);
    CypherParameters params = CypherParamBuilderFolder.createFolder(proxies.getLinkedDataUtil(), newRoot, null);

    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerFolder.class);
  }

  public FolderServerFolder findHomeFolderOf(CedarUserId userId) {
    String cypher = CypherQueryBuilderFolder.getHomeFolderOf();
    CypherParameters params = CypherParamBuilderUser.matchUserId(userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerFolder.class);
  }

  public FolderServerFolder findFolderById(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.getFolderById();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerFolder.class);
  }

  public List<FileSystemResource> findAllChildArtifactsOfFolder(CedarFolderId id) {
    String cypher = CypherQueryBuilderFolder.getAllChildArtifacts();
    CypherParameters params = CypherParamBuilderFolder.matchId(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FileSystemResource.class);
  }

  public long getFolderCount() {
    String cypher = CypherQueryBuilderFolder.getTotalCount();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }
}
