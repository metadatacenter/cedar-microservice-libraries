package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.server.neo4j.cypher.query.ModifiedDateConditions;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.*;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFolderContent;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderResource;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.server.security.model.auth.CedarPermission.READ_NOT_READABLE_NODE;

public class Neo4JProxyResource extends AbstractNeo4JProxy {

  Neo4JProxyResource(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  long findFolderContentsCount(CedarFolderId folderId, List<CedarResourceType> resourceTypeList, ResourceVersionFilter version,
                               ResourcePublicationStatusFilter publicationStatus) {
    return findFolderContentsCount(folderId, resourceTypeList, version, publicationStatus, ModifiedDateRange.ALL);
  }

  long findFolderContentsCount(CedarFolderId folderId, List<CedarResourceType> resourceTypeList, ResourceVersionFilter version,
                               ResourcePublicationStatusFilter publicationStatus, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsFilteredCountQuery(version, publicationStatus, modified);
    CypherParameters params = CypherParamBuilderFolderContent.getFolderContentsFilteredCountParameters(folderId, resourceTypeList, publicationStatus);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  long findFolderContentsUnfilteredCount(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsUnfilteredCountQuery();
    CypherParameters params = CypherParamBuilderFolder.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  List<FolderServerResourceExtract> findAllNodes(long limit, long offset, List<String> sortList) {
    String cypher = CypherQueryBuilderFilesystemResource.getAllResourcesLookupQuery(sortList);
    CypherParameters params = CypherParamBuilderFilesystemResource.getAllResourcesLookupParameters(limit, offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  private <T extends CedarResource> List<T> findFolderContentsFilteredGeneric(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                                              ResourceVersionFilter version,
                                                                              ResourcePublicationStatusFilter publicationStatus, int limit,
                                                                              int offset, List<String> sortList, CedarUser cu, Class<T> klazz) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsFilteredLookupQuery(sortList, version, publicationStatus);
    CypherParameters params = CypherParamBuilderFolderContent.getFolderContentsFilteredLookupParameters(folderId, resourceTypes, publicationStatus,
        limit, offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, klazz);
  }

  List<FileSystemResource> findFolderContentsFiltered(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                      ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, int limit,
                                                      int offset, List<String> sortList, CedarUser cu) {
    return findFolderContentsFilteredGeneric(folderId, resourceTypes, version, publicationStatus, limit, offset, sortList, cu,
        FileSystemResource.class);
  }

  List<FolderServerResourceExtract> findFolderContentsExtract(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                              ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                              long limit, long offset, List<String> sortList) {
    return findFolderContentsExtract(folderId, resourceTypes, version, publicationStatus, limit, offset, sortList, ModifiedDateRange.ALL);
  }

  List<FolderServerResourceExtract> findFolderContentsExtract(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                              ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                              long limit, long offset, List<String> sortList, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsFilteredLookupQuery(sortList, version, publicationStatus, modified);
    CypherParameters params = CypherParamBuilderFolderContent.getFolderContentsFilteredLookupParameters(folderId, resourceTypes, publicationStatus,
        limit, offset);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  List<Map<String, Object>> findFolderContentsExtractMap(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                         ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                         long limit, long offset, List<String> sortList, CedarUserId ownerId,
                                                         List<String> fieldNameList) {
    return findFolderContentsExtractMap(folderId, resourceTypes, version, publicationStatus, limit, offset, sortList, ownerId, fieldNameList, ModifiedDateRange.ALL);
  }

  List<Map<String, Object>> findFolderContentsExtractMap(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes,
                                                         ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                         long limit, long offset, List<String> sortList, CedarUserId ownerId,
                                                         List<String> fieldNameList, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsFilteredLookupQuery(sortList, version, publicationStatus, modified);
    CypherParameters params = CypherParamBuilderFolderContent.getFolderContentsFilteredLookupParameters(folderId, resourceTypes, publicationStatus,
        limit, offset);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetMapList(q, fieldNameList);
  }

  FileSystemResource findFilesystemResourceByParentFolderIdAndName(CedarFolderId parentId, String name) {
    String cypher = CypherQueryBuilderFilesystemResource.getResourceByParentIdAndName();
    CypherParameters params = CypherParamBuilderFilesystemResource.getResourceByParentIdAndName(parentId, name);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FileSystemResource.class);
  }

  void updateResourceOwner(CedarResourceId resourceId, CedarUserId userId) {
    boolean userExists = proxies.user().userExists(userId);
    if (userExists) {
      boolean resourceExists = proxies.resource().resourceExists(resourceId);
      if (resourceExists) {
        proxies.resource().updateOwner(resourceId, userId);
      }
    }
  }

  private boolean resourceExists(CedarResourceId resourceId) {
    String cypher = CypherQueryBuilderResource.resourceExists();
    CypherParameters params = CypherParamBuilderResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetBoolean(q);
  }

  public List<FolderServerResourceExtract> viewSharedWithMeFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                                    ResourcePublicationStatusFilter publicationStatus
      , int limit, int offset, List<String> sortList, CedarUserId ownerId) {
    return viewSharedWithMeFiltered(resourceTypes, version, publicationStatus, limit, offset, sortList, ownerId, ModifiedDateRange.ALL);
  }

  public List<FolderServerResourceExtract> viewSharedWithMeFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                                    ResourcePublicationStatusFilter publicationStatus
      , int limit, int offset, List<String> sortList, CedarUserId ownerId, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderResource.getSharedWithMeLookupQuery(version, publicationStatus, sortList, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSharedWithMeLookupParameters(resourceTypes, version, publicationStatus, limit
        , offset, ownerId);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  public List<FolderServerResourceExtract> viewSharedWithEverybodyFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                                           ResourcePublicationStatusFilter publicationStatus, int limit, int offset,
                                                                           List<String> sortList, CedarUserId ownerId) {
    return viewSharedWithEverybodyFiltered(resourceTypes, version, publicationStatus, limit, offset, sortList, ownerId, ModifiedDateRange.ALL);
  }

  public List<FolderServerResourceExtract> viewSharedWithEverybodyFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                                           ResourcePublicationStatusFilter publicationStatus, int limit, int offset,
                                                                           List<String> sortList, CedarUserId ownerId, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderFilesystemResource.getSharedWithEverybodyLookupQuery(version, publicationStatus, sortList, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSharedWithEverybodyLookupParameters(resourceTypes, version, publicationStatus
        , limit, offset, ownerId);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  public long viewSharedWithMeFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                            ResourcePublicationStatusFilter publicationStatus, CedarUserId ownerId) {
    return viewSharedWithMeFilteredCount(resourceTypes, version, publicationStatus, ownerId, ModifiedDateRange.ALL);
  }

  public long viewSharedWithMeFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                            ResourcePublicationStatusFilter publicationStatus, CedarUserId ownerId, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderResource.getSharedWithMeCountQuery(version, publicationStatus, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSharedWithMeCountParameters(resourceTypes, version, publicationStatus, ownerId);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public long viewSharedWithEverybodyFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                   ResourcePublicationStatusFilter publicationStatus, CedarUserId ownerId) {
    return viewSharedWithEverybodyFilteredCount(resourceTypes, version, publicationStatus, ownerId, ModifiedDateRange.ALL);
  }

  public long viewSharedWithEverybodyFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                   ResourcePublicationStatusFilter publicationStatus, CedarUserId ownerId, ModifiedDateRange modified) {
    String cypher = CypherQueryBuilderFilesystemResource.getSharedWithEverybodyCountQuery(version, publicationStatus, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSharedWithEverybodyCountParameters(resourceTypes, version, publicationStatus,
        ownerId);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public List<FolderServerResourceExtract> viewSpecialFoldersFiltered(int limit, int offset, List<String> sortList, CedarUser cu) {
    return viewSpecialFoldersFiltered(limit, offset, sortList, cu, ModifiedDateRange.ALL);
  }

  public List<FolderServerResourceExtract> viewSpecialFoldersFiltered(int limit, int offset, List<String> sortList, CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getSpecialFoldersLookupQuery(sortList, addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSpecialFoldersLookupParameters(limit, offset, cu.getResourceId());
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  public long viewSpecialFoldersFilteredCount(CedarUser cu) {
    return viewSpecialFoldersFilteredCount(cu, ModifiedDateRange.ALL);
  }

  public long viewSpecialFoldersFilteredCount(CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getSpecialFoldersCountQuery(addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSpecialFoldersCountParameters(cu.getResourceId());
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public List<FolderServerResourceExtract> viewAllFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                           ResourcePublicationStatusFilter publicationStatus, long limit, long offset,
                                                           List<String> sortList, CedarUser cu) {
    return viewAllFiltered(resourceTypes, version, publicationStatus, limit, offset, sortList, cu, ModifiedDateRange.ALL);
  }

  public List<FolderServerResourceExtract> viewAllFiltered(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                                           ResourcePublicationStatusFilter publicationStatus, long limit, long offset,
                                                           List<String> sortList, CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getAllLookupQuery(version, publicationStatus, sortList, addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getAllLookupParameters(resourceTypes, version, publicationStatus, limit, offset,
        cu.getResourceId(), addPermissionConditions);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  public long viewAllFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                   ResourcePublicationStatusFilter publicationStatus, CedarUser cu) {
    return viewAllFilteredCount(resourceTypes, version, publicationStatus, cu, ModifiedDateRange.ALL);
  }

  public long viewAllFilteredCount(List<CedarResourceType> resourceTypes, ResourceVersionFilter version,
                                   ResourcePublicationStatusFilter publicationStatus, CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getAllCountQuery(version, publicationStatus, addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getAllCountParameters(resourceTypes, version, publicationStatus,
        cu.getResourceId(), addPermissionConditions);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public List<FileSystemResource> findAllDescendantResourcesByFolderId(CedarFolderId folderId) {
    String cypher = CypherQueryBuilderFilesystemResource.getAllDescendantResources();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FileSystemResource.class);
  }

  public List<FileSystemResource> findAllFilesystemResourcesVisibleByGroupId(CedarGroupId id) {
    String cypher = CypherQueryBuilderFilesystemResource.getAllVisibleByGroupQuery();
    CypherParameters params = CypherParamBuilderGroup.matchId(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FileSystemResource.class);
  }

  public List<FolderServerResourceExtract> searchIsBasedOn(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOnId, int limit, int offset,
                                                           List<String> sortList, CedarUser cu) {
    return searchIsBasedOn(resourceTypes, isBasedOnId, limit, offset, sortList, cu, ModifiedDateRange.ALL);
  }

  public List<FolderServerResourceExtract> searchIsBasedOn(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOnId, int limit, int offset,
                                                           List<String> sortList, CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getSearchIsBasedOnLookupQuery(sortList, addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSearchIsBasedOnLookupParameters(resourceTypes, isBasedOnId, limit, offset,
        cu.getResourceId(), addPermissionConditions);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerResourceExtract.class);
  }

  public long searchIsBasedOnCount(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOn, CedarUser cu) {
    return searchIsBasedOnCount(resourceTypes, isBasedOn, cu, ModifiedDateRange.ALL);
  }

  public long searchIsBasedOnCount(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOn, CedarUser cu, ModifiedDateRange modified) {
    boolean addPermissionConditions = true;
    if (cu.has(READ_NOT_READABLE_NODE)) {
      addPermissionConditions = false;
    }
    String cypher = CypherQueryBuilderResource.getSearchIsBasedOnCountQuery(addPermissionConditions, modified);
    CypherParameters params = CypherParamBuilderFilesystemResource.getSearchIsBasedOnCountParameters(resourceTypes, isBasedOn, cu.getResourceId(),
        addPermissionConditions);
    ModifiedDateConditions.parameters(params, modified);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

  public boolean setEverybodyPermission(CedarFilesystemResourceId resourceId, NodeSharePermission everybodyPermission) {
    String cypher = CypherQueryBuilderFilesystemResource.setEverybodyPermission();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchResourceIdAndEverybodyPermission(resourceId, everybodyPermission);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting everybodyPermission");
  }

  public CedarResourceType getResourceType(CedarResourceId resourceId, CedarUserId ownerId) {
    String cypher = CypherQueryBuilderResource.getResourceTypeById();
    CypherParameters params = CypherParamBuilderResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    String typeString = executeReadGetString(q);
    return CedarResourceType.forValue(typeString);
  }

  boolean updateOwner(CedarResourceId resourceId, CedarUserId userId) {
    boolean removed = removeOwner(resourceId);
    if (removed) {
      return setOwner(resourceId, userId);
    }
    return false;
  }

  private boolean setOwner(CedarResourceId resourceId, CedarUserId userId) {
    String cypher = CypherQueryBuilderResource.setResourceOwner();
    CypherParameters params = CypherParamBuilderResource.matchResourceAndUser(resourceId, userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "setting owner");
  }

  private boolean removeOwner(CedarResourceId resourceId) {
    String cypher = CypherQueryBuilderResource.removeResourceOwner();
    CypherParameters params = CypherParamBuilderResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing owner");
  }

  public boolean isFileSystemResourceOpenImplicitly(CedarFilesystemResourceId cedarFilesystemResourceIdId) {
    String cypher = CypherQueryBuilderFilesystemResource.isFileSystemResourceOpenImplicitly();
    CypherParameters params = CypherParamBuilderFilesystemResource.getResourceByIdParameters(proxies.pathUtil.getRootPath(), cedarFilesystemResourceIdId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    List<FileSystemResource> fileSystemResources = executeReadGetList(q, FileSystemResource.class);
    return fileSystemResources.size() > 0;
  }
}
