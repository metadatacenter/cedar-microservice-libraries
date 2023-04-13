package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderResource;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;
import java.util.Map;

public class Neo4JProxyArtifact extends AbstractNeo4JProxy {

  Neo4JProxyArtifact(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  FolderServerArtifact createResourceAsChildOfId(FolderServerArtifact newResource, CedarFolderId parentId) {
    String cypher = CypherQueryBuilderArtifact.createResourceAsChildOfId(newResource);
    CypherParameters params = CypherParamBuilderArtifact.createArtifact(newResource, parentId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerArtifact.class);
  }

  FolderServerArtifact updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    String cypher = CypherQueryBuilderArtifact.updateResourceById(updateFields);
    CypherParameters params = CypherParamBuilderArtifact.updateArtifactById(artifactId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerArtifact.class);
  }

  boolean deleteArtifactById(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.deleteArtifactById();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "deleting artifact");
  }

  boolean moveArtifact(CedarArtifactId sourceArtifactId, CedarFolderId targetFolderId) {
    boolean unlink = unlinkResourceFromParent(sourceArtifactId);
    if (unlink) {
      return linkArtifactUnderFolder(sourceArtifactId, targetFolderId);
    }
    return false;
  }

  private boolean unlinkResourceFromParent(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.unlinkArtifactFromParentFolder();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "unlinking artifact");
  }

  private boolean linkArtifactUnderFolder(CedarArtifactId artifactId, CedarFolderId parentFolderId) {
    String cypher = CypherQueryBuilderArtifact.linkArtifactUnderFolder();
    CypherParameters params = AbstractCypherParamBuilder.matchArtifactIdAndParentFolderId(artifactId, parentFolderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "linking artifact");
  }

  private boolean setOwner(CedarArtifactId artifactId, CedarUserId newOwnerId) {
    String cypher = CypherQueryBuilderArtifact.setArtifactOwner();
    CypherParameters params = CypherParamBuilderArtifact.matchArtifactIdAndUserId(artifactId, newOwnerId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting owner");
  }

  boolean updateOwner(CedarArtifactId artifactId, CedarUserId newOwnerId) {
    boolean removed = removeOwner(artifactId);
    if (removed) {
      return setOwner(artifactId, newOwnerId);
    }
    return false;
  }

  boolean removeOwner(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.removeResourceOwner();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing owner");
  }

  private <T extends CedarResource> T findResourceGenericById(CedarResourceId id, Class<T> klazz) {
    String cypher = CypherQueryBuilderResource.getResourceById();
    CypherParameters params = CypherParamBuilderResource.matchId(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, klazz);
  }

  public FolderServerArtifactExtract findResourceExtractById(CedarArtifactId artifactId) {
    return findResourceGenericById(artifactId, FolderServerArtifactExtract.class);
  }

  public FolderServerArtifact findArtifactById(CedarArtifactId artifactId) {
    return findResourceGenericById(artifactId, FolderServerArtifact.class);
  }

  public FolderServerSchemaArtifact findSchemaArtifactById(CedarSchemaArtifactId artifactId) {
    return findResourceGenericById(artifactId, FolderServerSchemaArtifact.class);
  }

  public boolean setDerivedFrom(CedarArtifactId newId, CedarArtifactId oldId) {
    String cypher = CypherQueryBuilderArtifact.setDerivedFrom();
    CypherParameters params = CypherParamBuilderResource.matchSourceAndTarget(newId, oldId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting derivedFrom");
  }

  public boolean unsetLatestVersion(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.unsetLatestVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "unsetting isLatestVersion");
  }

  public boolean setLatestVersion(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.setLatestVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isLatestVersion");
  }

  public boolean unsetLatestDraftVersion(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.unsetLatestDraftVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "unsetting isLatestDraftVersion");
  }

  public boolean setLatestPublishedVersion(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.setLatestPublishedVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isLatestPublishedVersion");
  }

  public boolean unsetLatestPublishedVersion(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.unsetLatestPublishedVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "unsetting isLatestPublishedVersion");
  }

  public long getIsBasedOnCount(CedarTemplateId templateId) {
    String cypher = CypherQueryBuilderArtifact.getIsBasedOnCount();
    CypherParameters params = CypherParamBuilderArtifact.matchId(templateId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public List<FolderServerArtifactExtract> getVersionHistory(CedarSchemaArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.getVersionHistory();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerArtifactExtract.class);
  }

  public List<FolderServerArtifactExtract> getVersionHistoryWithPermission(CedarSchemaArtifactId artifactId, CedarUserId userId) {
    String cypher = CypherQueryBuilderArtifact.getVersionHistoryWithPermission();
    CypherParameters params = CypherParamBuilderArtifact.matchArtifactIdAndUserId(artifactId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerArtifactExtract.class);
  }

  public boolean setOpen(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.setOpen();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isOpen");
  }

  public boolean setNotOpen(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.setNotOpen();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isOpen");
  }

  public boolean setOpen(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.setOpen();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isOpen");
  }

  public boolean setNotOpen(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolder.setNotOpen();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isOpen");
  }

}
