package org.metadatacenter.server;

import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtractWithChildren;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;

import java.util.List;
import java.util.Map;

public interface CategoryServiceSession {

  FolderServerCategory createCategory(CedarCategoryId parentId, String name, String description, String identifier);

  FolderServerCategory getCategoryById(CedarCategoryId categoryId);

  FolderServerCategory getCategoryByParentAndName(CedarCategoryId parentId, String name);

  List<FolderServerCategory> getAllCategories(int limit, int offset);

  long getCategoryCount();

  FolderServerCategory updateCategoryById(CedarCategoryId categoryId, Map<NodeProperty, String> updateFields);

  boolean deleteCategoryById(CedarCategoryId categoryId);

  FolderServerCategory getRootCategory();

  List<FolderServerCategory> getChildrenOf(CedarCategoryId parentCategoryId, int limit, int offset);

  FolderServerCategoryExtractWithChildren getCategoryTree();

  Object getCategoryPermissions(CedarCategoryId categoryId);

  Object updateCategoryPermissions(CedarCategoryId categoryId, Object permissions);

  Object getCategoryDetails(CedarCategoryId categoryId);

  boolean attachCategoryToArtifact(CedarCategoryId categoryId, CedarArtifactId artifactId);

  boolean detachCategoryFromArtifact(CedarCategoryId categoryId, CedarArtifactId artifactId);

  CedarNodeMaterializedCategories getArtifactMaterializedCategories(CedarArtifactId artifactId);

  List<List<FolderServerCategoryExtract>> getAttachedCategoryPaths(CedarArtifactId artifactId);

  List<CedarCategoryId> getAttachedCategoryIds(CedarArtifactId artifactId);

  List<FolderServerCategoryExtract> getCategoryPath(CedarCategoryId cid);
}
