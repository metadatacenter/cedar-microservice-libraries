package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.server.ArtifactGraphUpdateResult;

import org.metadatacenter.server.neo4j.ArtifactRestoreTransaction;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox;
import org.metadatacenter.server.neo4j.VersionChainTransaction;
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

  private volatile boolean versioningInitialized;

  protected synchronized void initializeVersioning() {
    if (!versioningInitialized) {
      VersionChainTransaction.initialize(driver);
      versioningInitialized = true;
    }
  }

  FolderServerArtifact createResourceAsChildOfId(FolderServerArtifact newResource, CedarFolderId parentId) {
    return createResourceAsChildOfId(newResource, parentId, null);
  }

  FolderServerArtifact createResourceAsChildOfId(FolderServerArtifact newResource, CedarFolderId parentId, String jobId) {
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      VersionChainTransaction.lock(tx);
      ArtifactCreateCleanupOutbox.requireRegistration(tx, jobId, newResource.getId());
      var created = runInTransactionGetOne(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.createResourceAsChildOfId(newResource),
          CypherParamBuilderArtifact.createArtifact(newResource, parentId)), FolderServerArtifact.class);
      if (created != null) ArtifactCreateCleanupOutbox.registered(tx, jobId);
      return created;
    }, "registering an artifact and retiring failed-create cleanup");
  }

  FolderServerArtifact createInstanceCloneAsChildOfId(FolderServerArtifact clone, CedarArtifactId sourceId,
                                                      CedarFolderId parentId, CedarUserId expectedOwner) {
    return createInstanceCloneAsChildOfId(clone, sourceId, parentId, expectedOwner, null);
  }

  FolderServerArtifact createInstanceCloneAsChildOfId(FolderServerArtifact clone, CedarArtifactId sourceId,
      CedarFolderId parentId, CedarUserId expectedOwner, String jobId) {
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      VersionChainTransaction.lock(tx);
      ArtifactCreateCleanupOutbox.requireRegistration(tx, jobId, clone.getId());
      // Ownership transfers take these same node locks. Check after locking, and hold them through
      // registration so a transfer cannot land between the ownership check and the new OWNS arc.
      for (String id : java.util.stream.Stream.of(sourceId.getId(), parentId.getId()).sorted().toList()) {
        var locked = tx.run("MATCH (n {_id:$id}) SET n._cedarAclRevision=coalesce(n._cedarAclRevision,1) "
            + "RETURN n._cedarAclRevision AS revision", Map.of("id", id));
        if (readLockedRevision(locked).isEmpty()) return null;
      }
      var owners = tx.run("MATCH (u:User {_id:$owner})-[:OWNS]->(s:Artifact {_id:$source}), "
          + "(u)-[:OWNS]->(p:Folder {_id:$parent}) RETURN s",
          Map.of("owner", expectedOwner.getId(), "source", sourceId.getId(), "parent", parentId.getId()));
      if (!owners.hasNext()) return null;
      var created = runInTransactionGetOne(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.createResourceAsChildOfId(clone),
          CypherParamBuilderArtifact.createArtifact(clone, parentId)), FolderServerArtifact.class);
      if (created != null) {
        var query = new CypherQueryWithParameters(CypherQueryBuilderArtifact.setDerivedFrom(),
            CypherParamBuilderResource.matchSourceAndTarget(created.getResourceId(), sourceId));
        tx.run(query.getRunnableQuery(), query.getParameterMap()).consume();
        created.setDerivedFrom(CedarUntypedArtifactId.build(sourceId.getId()));
        ArtifactCreateCleanupOutbox.registered(tx, jobId);
      }
      return created;
    }, "registering a clone for its unchanged owner");
  }

  FolderServerArtifact createDraftAsChildOfId(FolderServerArtifact draft, CedarFolderId parentId, boolean propagateSharing) {
    return createDraftAsChildOfId(draft, parentId, propagateSharing, null);
  }

  FolderServerArtifact createDraftAsChildOfId(FolderServerArtifact draft, CedarFolderId parentId,
      boolean propagateSharing, String jobId) {
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      VersionChainTransaction.lock(tx);
      ArtifactCreateCleanupOutbox.requireRegistration(tx, jobId, draft.getId());
      var schema = (FolderServerSchemaArtifact) draft;
      VersionChainTransaction.requireDraftSource(tx, schema.getPreviousVersion().getId(), schema.getVersion().getValue());
      var created = runInTransactionGetOne(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.createResourceAsChildOfId(draft),
          CypherParamBuilderArtifact.createArtifact(draft,parentId)), FolderServerArtifact.class);
      if (created != null) {
        if (propagateSharing) {
          Map<String,Object> args=Map.of("source",schema.getPreviousVersion().getId(),"target",created.getId());
          for (String role : List.of("CANREAD","CANWRITE","EDITOR_ROLE","VIEWER_ROLE","MANAGER_ROLE")) {
            tx.run("MATCH (p)-[r:" + role + "]->(s:Artifact {_id:$source}), (d:Artifact {_id:$target}) "
                + "MERGE (p)-[copy:" + role + "]->(d) SET copy=properties(r)",args).consume();
          }
          tx.run("MATCH (s:Artifact {_id:$source}), (d:Artifact {_id:$target}) "
              + "SET d.everybodyPermission=s.everybodyPermission",args).consume();
        }
        VersionChainTransaction.reconcile(tx,created.getId());
        ArtifactCreateCleanupOutbox.registered(tx, jobId);
      }
      return created;
    }, "creating the sole successor draft");
  }

  FolderServerArtifact updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    return updateArtifactById(artifactId, updateFields, updatedBy, null, null).resource();
  }

  ArtifactGraphUpdateResult updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields,
      CedarUserId updatedBy, String restoreJobId) {
    return updateArtifactById(artifactId, updateFields, updatedBy, restoreJobId, null);
  }

  ArtifactGraphUpdateResult updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields,
      CedarUserId updatedBy, String restoreJobId, String projectionContent) {
    return updateArtifactById(artifactId, updateFields, updatedBy, restoreJobId, projectionContent, null);
  }

  ArtifactGraphUpdateResult updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields,
      CedarUserId updatedBy, String restoreJobId, String projectionContent,
      org.metadatacenter.server.ArtifactModificationProvenance modificationProvenance) {
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      // Use the relay's lock before touching graph state: an in-flight projection cannot be
      // overtaken by a save, publication or deletion, including across resource-server processes.
      VersionChainTransaction.lock(tx);
      var decision = restoreJobId == null ? ArtifactRestoreTransaction.GraphDecision.READY
          : ArtifactRestoreTransaction.graphDecision(tx, artifactId.getId(), restoreJobId);
      if (decision != ArtifactRestoreTransaction.GraphDecision.READY) {
        var outcome = switch (decision) {
          case SUPERSEDED -> ArtifactGraphUpdateResult.Outcome.SUPERSEDED;
          case RESTORED -> ArtifactGraphUpdateResult.Outcome.RESTORED;
          default -> ArtifactGraphUpdateResult.Outcome.FAILED;
        };
        return new ArtifactGraphUpdateResult(null, outcome);
      }
      if (updateFields.containsKey(NodeProperty.PUBLICATION_STATUS)
          && !VersionChainTransaction.requirePublish(tx, artifactId.getId(), updateFields.get(NodeProperty.VERSION))) {
        return ArtifactGraphUpdateResult.updated(null);
      }
      FolderServerArtifact result = runInTransactionGetOne(tx, new CypherQueryWithParameters(
          CypherQueryBuilderArtifact.updateResourceById(updateFields),
          CypherParamBuilderArtifact.updateArtifactById(artifactId, updateFields, updatedBy, modificationProvenance)),
          FolderServerArtifact.class);
      if (result != null) {
        if (updateFields.containsKey(NodeProperty.PUBLICATION_STATUS)) VersionChainTransaction.reconcile(tx,artifactId.getId());
        if (projectionContent == null) VersionChainTransaction.enqueue(tx, artifactId.getId(), false);
        else VersionChainTransaction.enqueueContent(tx, artifactId.getId(), projectionContent);
        if (restoreJobId != null) ArtifactRestoreTransaction.remove(tx, restoreJobId);
      }
      return ArtifactGraphUpdateResult.updated(result);
    }, "updating an artifact and completing its compensation record");
  }

  boolean deleteArtifactById(CedarArtifactId artifactId) {
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      VersionChainTransaction.delete(tx,artifactId.getId());
      return true;
    }, "deleting an artifact and reconnecting its version series");
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

  public long getIsBasedOnCount(CedarTemplateId templateId) {
    String cypher = CypherQueryBuilderArtifact.getIsBasedOnCount();
    CypherParameters params = CypherParamBuilderArtifact.matchId(templateId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public boolean enqueueInstanceReindex(CedarTemplateId templateId) {
    String cypher = CypherQueryBuilderArtifact.enqueueInstanceReindex();
    CypherParameters params = CypherParamBuilderArtifact.matchId(templateId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "marking a template's instances for reindexing");
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
    initializeVersioning();
    return executeInWriteTransaction(tx -> {
      VersionChainTransaction.lock(tx);
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
      var updated = readVersionedResource(run(tx, new CypherQueryWithParameters(cypher, params)),
          FolderServerArtifact.class);
      if (updated != null) VersionChainTransaction.enqueue(tx, artifactId.getId(), false);
      return updated;
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
    JsonNode json = JsonMapper.STRICT_MAPPER.valueToTree(node.asMap());
    return new VersionedResource<>(buildClass(json, type), record.get("revision").asLong());
  }

}
