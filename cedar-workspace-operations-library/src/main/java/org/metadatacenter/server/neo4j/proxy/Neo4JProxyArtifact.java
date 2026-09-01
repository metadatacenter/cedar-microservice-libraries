package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFolder;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderResource;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.types.Node;

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
    return moveArtifact(sourceArtifactId, targetFolderId, RevisionPrecondition.any()) != null;
  }

  VersionedResource<FolderServerArtifact> moveArtifact(CedarArtifactId sourceArtifactId,
                                                        CedarFolderId targetFolderId,
                                                        RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      Result locked = run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.lockArtifactRevision(),
          CypherParamBuilderArtifact.matchId(sourceArtifactId)));
      if (!locked.hasNext()) {
        return null;
      }
      long currentRevision = locked.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      CypherParameters params = CypherParamBuilderArtifact.matchArtifactIdAndParentFolderId(
          sourceArtifactId, targetFolderId);
      return readVersionedResource(run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.moveArtifact(), params)), FolderServerArtifact.class);
    }, "moving a versioned artifact");
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

  VersionedResource<FolderServerArtifact> findVersionedArtifactById(CedarArtifactId artifactId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderArtifact.getVersionedArtifactById(), CypherParamBuilderArtifact.matchId(artifactId));
    return executeInReadTransaction(tx -> readVersionedResource(run(tx, query), FolderServerArtifact.class),
        "reading a versioned artifact");
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

  VersionedResource<FolderServerArtifact> setOpen(CedarArtifactId artifactId,
                                                   RevisionPrecondition precondition) {
    return setArtifactOpenState(artifactId, precondition, true);
  }

  public boolean setNotOpen(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderArtifact.setNotOpen();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting isOpen");
  }

  VersionedResource<FolderServerArtifact> setNotOpen(CedarArtifactId artifactId,
                                                      RevisionPrecondition precondition) {
    return setArtifactOpenState(artifactId, precondition, false);
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

  private VersionedResource<FolderServerArtifact> setArtifactOpenState(CedarArtifactId artifactId,
                                                                        RevisionPrecondition precondition,
                                                                        boolean open) {
    return executeInWriteTransaction(tx -> {
      CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
      Result locked = run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.lockArtifactRevision(), params));
      if (!locked.hasNext()) {
        return null;
      }
      long currentRevision = locked.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      String cypher = open ? CypherQueryBuilderArtifact.setOpen() : CypherQueryBuilderArtifact.setNotOpen();
      return readVersionedResource(run(tx, new CypherQueryWithParameters(cypher, params)),
          FolderServerArtifact.class);
    }, open ? "making an artifact open" : "making an artifact not open");
  }

  private Result run(Transaction tx, CypherQueryWithParameters query) {
    return tx.run(query.getRunnableQuery(), query.getParameterMap());
  }

  private <T extends CedarResource> VersionedResource<T> readVersionedResource(Result result, Class<T> type) {
    if (!result.hasNext()) {
      return null;
    }
    Record record = result.next();
    Node node = record.get("resource").asNode();
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return new VersionedResource<>(buildClass(json, type), record.get("revision").asLong());
  }

}
