package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderGroup;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.server.CategoryNotEmptyException;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.types.Node;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

public class Neo4JProxyCategory extends AbstractNeo4JProxy {

  Neo4JProxyCategory(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public FolderServerCategory getRootCategory() {
    String cypher = CypherQueryBuilderCategory.getRootCategory();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory createCategory(CedarCategoryId parentCategoryId, CedarCategoryId newCategoryId, String categoryName,
                                             String categoryDescription, String categoryIdentifier, CedarUserId userId) {
    String cypher = CypherQueryBuilderCategory.createCategory(parentCategoryId, userId);
    CypherParameters params = CypherParamBuilderCategory.createCategory(parentCategoryId, newCategoryId, categoryName, categoryDescription,
        categoryIdentifier, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory getCategoryByParentAndName(CedarCategoryId parentId, String name) {
    String cypher = CypherQueryBuilderCategory.getCategoryByParentAndName();
    CypherParameters params = CypherParamBuilderCategory.getCategoryByParentAndName(parentId, name);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public FolderServerCategory getCategoryById(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryById();
    CypherParameters params = CypherParamBuilderCategory.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public VersionedResource<FolderServerCategory> getVersionedCategoryById(CedarCategoryId categoryId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderCategory.getVersionedCategoryById(), CypherParamBuilderCategory.matchId(categoryId));
    return executeInReadTransaction(tx -> readVersionedCategory(run(tx, query)), "reading a versioned category");
  }

  public FolderServerCategory getCategoryByIdentifier(String identifier) {
    String cypher = CypherQueryBuilderCategory.getCategoryByIdentifier();
    CypherParameters params = CypherParamBuilderCategory.matchIdentifier(identifier);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerCategory.class);
  }

  public List<FolderServerCategory> getAllCategories(int limit, int offset) {
    String cypher = CypherQueryBuilderCategory.getAllCategories();
    CypherParameters params = CypherParamBuilderCategory.getAllCategories(limit, offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }

  public long getCategoryCount() {
    String cypher = CypherQueryBuilderCategory.getTotalCount();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public FolderServerCategory updateCategoryById(CedarCategoryId categoryId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    String cypher = CypherQueryBuilderGroup.updateCategoryById(updateFields);
    CypherParameters params = CypherParamBuilderCategory.updateCategoryById(categoryId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerCategory.class);
  }

  public VersionedResource<FolderServerCategory> updateCategoryById(CedarCategoryId categoryId,
                                                                     Map<NodeProperty, String> updateFields,
                                                                     CedarUserId updatedBy,
                                                                     RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      Result locked = run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderCategory.lockCategoryRevision(), CypherParamBuilderCategory.matchId(categoryId)));
      OptionalLong lockedRevision = readLockedRevision(locked);
      if (lockedRevision.isEmpty()) {
        return null;
      }
      long currentRevision = lockedRevision.getAsLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      CypherParameters params = CypherParamBuilderCategory.updateCategoryById(categoryId, updateFields, updatedBy);
      return readVersionedCategory(run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderGroup.updateCategoryById(updateFields), params)));
    }, "updating a versioned category");
  }

  public boolean deleteCategoryById(CedarCategoryId categoryId) {
    return deleteCategoryById(categoryId, RevisionPrecondition.any());
  }

  public boolean deleteCategoryById(CedarCategoryId categoryId, RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderCategory.lockCategoryRevision(), CypherParamBuilderCategory.matchId(categoryId));
      Result locked = run(tx, lock);
      OptionalLong lockedRevision = readLockedRevision(locked);
      if (lockedRevision.isEmpty()) {
        return false;
      }
      long currentRevision = lockedRevision.getAsLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      CypherQueryWithParameters blockersQuery = new CypherQueryWithParameters(
          CypherQueryBuilderCategory.getCategoryDeletionBlockers(), CypherParamBuilderCategory.matchId(categoryId));
      Record blockers = run(tx, blockersQuery).single();
      long childCategoryCount = blockers.get("childCategoryCount").asLong();
      long artifactCount = blockers.get("artifactCount").asLong();
      if (childCategoryCount > 0 || artifactCount > 0) {
        throw new CategoryNotEmptyException(childCategoryCount, artifactCount);
      }
      CypherQueryWithParameters delete = new CypherQueryWithParameters(
          CypherQueryBuilderCategory.deleteCategoryById(), CypherParamBuilderCategory.matchId(categoryId));
      return run(tx, delete).hasNext();
    }, "deleting a versioned category");
  }

  private Result run(Transaction tx, CypherQueryWithParameters query) {
    return tx.run(query.getRunnableQuery(), query.getParameterMap());
  }

  private VersionedResource<FolderServerCategory> readVersionedCategory(Result result) {
    if (!result.hasNext()) {
      return null;
    }
    Record record = result.next();
    Node node = record.get("resource").asNode();
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return new VersionedResource<>(buildClass(json, FolderServerCategory.class), record.get("revision").asLong());
  }

  public FolderServerUser getCategoryOwner(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryOwner();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerUser.class);
  }

  public boolean attachCategoryToArtifact(CedarCategoryId categoryId, CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderCategory.attachCategoryToArtifact();
    CypherParameters params = CypherParamBuilderCategory.categoryIdAndArtifactId(categoryId, artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerCategory category = executeWriteGetOne(q, FolderServerCategory.class);
    return category != null;
  }

  public boolean attachCategoriesToArtifact(List<CedarCategoryId> categoryIds, CedarArtifactId artifactId) {
    List<String> distinctCategoryIds = categoryIds.stream()
        .map(CedarCategoryId::getId)
        .distinct()
        .toList();
    if (distinctCategoryIds.isEmpty()) {
      return false;
    }
    String cypher = CypherQueryBuilderCategory.attachCategoriesToArtifact();
    CypherParameters params = CypherParamBuilderCategory.categoryIdsAndArtifactId(distinctCategoryIds, artifactId);
    CypherQueryWithParameters query = new CypherQueryWithParameters(cypher, params);
    return executeInWriteTransaction(tx -> {
      Result result = run(tx, query);
      if (!result.hasNext()) {
        return false;
      }
      return result.next().get("attachedCount").asLong() == distinctCategoryIds.size();
    }, "attaching categories to artifact");
  }

  public boolean detachCategoryFromArtifact(CedarCategoryId categoryId, CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderCategory.detachCategoryFromArtifact();
    CypherParameters params = CypherParamBuilderCategory.categoryIdAndArtifactId(categoryId, artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerCategory category = executeWriteGetOne(q, FolderServerCategory.class);
    return category != null;
  }

  public List<FolderServerCategory> getCategoryPaths(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderCategory.getCategoryPathsByArtifactId();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }

  public List<CedarCategoryId> getCategoryPathIds(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderCategory.getCategoryPathIdsByArtifactId();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarCategoryId.class);
  }

  public List<CedarCategoryId> getCategoryIds(CedarArtifactId artifactId) {
    String cypher = CypherQueryBuilderCategory.getCategoryIdsByArtifactId();
    CypherParameters params = CypherParamBuilderArtifact.matchId(artifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetIdList(q, CedarCategoryId.class);
  }


  public void updateCategoryOwner(CedarCategoryId categoryId, CedarUserId newOwnerId) {
    boolean userExists = proxies.user().userExists(newOwnerId);
    if (userExists) {
      boolean categoryExists = proxies.category().categoryExists(categoryId);
      if (categoryExists) {
        proxies.category().updateOwner(categoryId, newOwnerId);
      }
    }
  }

  private boolean setOwner(CedarCategoryId categoryId, CedarUserId userId) {
    String cypher = CypherQueryBuilderCategory.setCategoryOwner();
    CypherParameters params = CypherParamBuilderCategory.matchCategoryAndUser(categoryId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting owner");
  }

  private boolean removeOwner(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.removeCategoryOwner();
    CypherParameters params = CypherParamBuilderCategory.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing owner");
  }

  boolean updateOwner(CedarCategoryId categoryId, CedarUserId userId) {
    boolean removed = removeOwner(categoryId);
    if (removed) {
      return setOwner(categoryId, userId);
    }
    return false;
  }

  private boolean categoryExists(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.categoryExists();
    CypherParameters params = CypherParamBuilderCategory.matchId(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetBoolean(q);
  }

  public List<FolderServerCategory> getCategoryPath(CedarCategoryId categoryId) {
    String cypher = CypherQueryBuilderCategory.getCategoryPath();
    CypherParameters params = CypherParamBuilderCategory.getCategoryById(categoryId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerCategory.class);
  }
}
