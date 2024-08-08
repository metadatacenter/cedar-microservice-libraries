package org.metadatacenter.server.search.elasticsearch.service;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.config.TrustedFoldersConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.IndexedDocumentDocument;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchPermissionEnabledContentSearchingWorker;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchSearchingWorker;
import org.metadatacenter.server.search.elasticsearch.worker.SearchResponseResult;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.util.TrustedByUtil;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.constant.ElasticsearchConstants.*;

public class NodeSearchingService extends AbstractSearchingService {

  private static final Logger log = LoggerFactory.getLogger(NodeSearchingService.class);

  private final RestHighLevelClient client;
  private final OpensearchConfig config;
  private final TrustedFoldersConfig trustedFoldersConfig;
  private final ElasticsearchPermissionEnabledContentSearchingWorker permissionEnabledSearchWorker;
  private final ElasticsearchSearchingWorker searchWorker;

  NodeSearchingService(CedarConfig cedarConfig, RestHighLevelClient client) {
    this.client = client;
    this.config = cedarConfig.getElasticsearchConfig();
    this.trustedFoldersConfig = cedarConfig.getTrustedFolders();
    permissionEnabledSearchWorker = new ElasticsearchPermissionEnabledContentSearchingWorker(cedarConfig.getElasticsearchConfig(), client);
    searchWorker = new ElasticsearchSearchingWorker(cedarConfig.getElasticsearchConfig(), client);
  }

  public IndexedDocumentId getByCedarId(String resourceId) throws CedarProcessingException {
    return getByCedarId(client, resourceId, config.getIndexes().getSearchIndex().getName(), IndexedDocumentType.DOC.getValue());
  }

  public Map<String, Object> getDocumentByCedarId(CedarResourceId resourceId) throws CedarProcessingException {
    try {
      // Create the search request
      SearchRequest searchRequest = new SearchRequest(config.getIndexes().getSearchIndex().getName());
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, resourceId.getId()));
      searchRequest.source(searchSourceBuilder);

      // Execute the search request
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      // Process the search hits
      for (SearchHit hit : searchResponse.getHits().getHits()) {
        if (hit != null) {
          return hit.getSourceAsMap();
        }
      }
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
    return null;
  }

  public List<String> findAllValuesForField(String fieldName) throws CedarProcessingException {
    return searchWorker.findAllValuesForField(fieldName);
  }

  public List<String> findAllCedarIdsForGroup(CedarGroupId groupId) throws CedarProcessingException {

    BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
    BoolQueryBuilder permissionQuery = QueryBuilders.boolQuery();

    QueryBuilder groupReadQuery = QueryBuilders.termsQuery(GROUPS, CedarNodeMaterializedPermissions.getKey(groupId.getId(),
        FilesystemResourcePermission.READ));
    QueryBuilder groupWriteQuery = QueryBuilders.termsQuery(GROUPS, CedarNodeMaterializedPermissions.getKey(groupId.getId(),
        FilesystemResourcePermission.WRITE));

    permissionQuery.should(groupReadQuery);
    permissionQuery.should(groupWriteQuery);
    mainQuery.must(permissionQuery);

    return searchWorker.findAllValuesForField("cid", mainQuery);
  }

  public FolderServerNodeListResponse search(CedarRequestContext rctx, String query, String id, List<String> resourceTypes,
                                             ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, String categoryId,
                                             List<String> sortList, int limit, int offset, String absoluteUrl) throws CedarProcessingException {
    try {
      SearchResponseResult searchResult = permissionEnabledSearchWorker.search(rctx, query, resourceTypes, version, publicationStatus, categoryId,
          sortList, limit, offset);
      return assembleResponse(rctx, searchResult, query, id, resourceTypes, version, publicationStatus, categoryId, sortList, limit, offset,
          absoluteUrl);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  public SearchResponseResult search(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                     ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, String categoryId,
                                     List<String> sortList, int limit, int offset) throws CedarProcessingException {
    try {
      return permissionEnabledSearchWorker.search(rctx, query, resourceTypes, version, publicationStatus, categoryId, sortList, limit, offset);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  public FolderServerNodeListResponse searchDeep(CedarRequestContext rctx, String query, String id, List<String> resourceTypes,
                                                 ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                 String categoryId, List<String> sortList, int limit, int offset, String absoluteUrl) throws CedarProcessingException {
    try {
      SearchResponseResult searchResult = permissionEnabledSearchWorker.searchDeep(rctx, query, resourceTypes, version, publicationStatus,
          categoryId, sortList, limit, offset);
      return assembleResponse(rctx, searchResult, query, id, resourceTypes, version, publicationStatus, categoryId, sortList, limit, offset,
          absoluteUrl);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  private FolderServerNodeListResponse assembleResponse(CedarRequestContext rctx, SearchResponseResult searchResult, String query, String id,
                                                        List<String> resourceTypes, ResourceVersionFilter version,
                                                        ResourcePublicationStatusFilter publicationStatus, String categoryId, List<String> sortList
      , int limit, int offset, String absoluteUrl) {
    List<FolderServerResourceExtract> resources = new ArrayList<>();

    // Get the object from the result
    for (SearchHit hit : searchResult.getHits()) {
      String hitJson = hit.getSourceAsString();
      try {
        IndexedDocumentDocument indexedDocument = JsonMapper.MAPPER.readValue(hitJson, IndexedDocumentDocument.class);

        FolderServerNodeInfo info = indexedDocument.getInfo();
        FolderServerResourceExtract folderServerNodeExtract = FolderServerResourceExtract.fromNodeInfo(info);
        TrustedByUtil.decorateWithTrustedBy(folderServerNodeExtract, info.getParentFolderId(), trustedFoldersConfig.getFoldersMap());
        resources.add(folderServerNodeExtract);
      } catch (IOException e) {
        log.error("Error while deserializing the search result document", e);
      }
    }

    FolderServerNodeListResponse response = new FolderServerNodeListResponse();

    long total = searchResult.getTotalCount();

    response.setTotalCount(total);
    response.setCurrentOffset(offset);
    response.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, limit, offset));
    response.setResources(resources);

    List<CedarResourceType> resourceTypeList = new ArrayList<>();
    if (resourceTypes != null) {
      for (String rt : resourceTypes) {
        resourceTypeList.add(CedarResourceType.forValue(rt));
      }
    }

    NodeListRequest req = new NodeListRequest();
    req.setResourceTypes(resourceTypeList);
    req.setVersion(version);
    req.setPublicationStatus(publicationStatus);
    req.setCategoryId(categoryId);
    CedarCategoryId cid = CedarCategoryId.build(categoryId);
    CategoryServiceSession categorySession = CedarDataServices.getCategoryServiceSession(rctx);
    FolderServerCategory category = categorySession.getCategoryById(cid);
    if (category != null) {
      response.setCategoryName(category.getName());
      List<FolderServerCategoryExtract> categoryPath = categorySession.getCategoryPath(cid);
      response.setCategoryPath(categoryPath);
    }
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);
    req.setQ(query);
    req.setId(id);
    response.setRequest(req);

    return response;
  }

  public long searchAccessibleResourceCountByUser(List<String> resourceTypes, FilesystemResourcePermission permission, CedarUser user) throws CedarProcessingException {
    try {
      return permissionEnabledSearchWorker.searchAccessibleResourceCountByUser(resourceTypes, permission, user);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  public long getTotalCount(CedarResourceType resourceType) throws CedarProcessingException {
    try {
      // Create the search request
      SearchRequest searchRequest = new SearchRequest(config.getIndexes().getSearchIndex().getName());
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(QueryBuilders.matchQuery(RESOURCE_TYPE, resourceType.getValue()));
      searchSourceBuilder.size(0); // We only need the count, no hits
      searchSourceBuilder.trackTotalHits(true); // Ensure total hits are tracked
      searchRequest.source(searchSourceBuilder);

      // Execute the search request
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      // Process the search hits
      SearchHits hits = searchResponse.getHits();
      return hits.getTotalHits().value;
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public long getTotalArtifactCount() throws CedarProcessingException {
    try {
      // Create the search request
      SearchRequest searchRequest = new SearchRequest(config.getIndexes().getSearchIndex().getName());
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(QueryBuilders.matchAllQuery());
      searchSourceBuilder.size(0); // We only need the count, no hits
      searchSourceBuilder.trackTotalHits(true); // Ensure total hits are tracked
      searchRequest.source(searchSourceBuilder);

      // Execute the search request
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      // Process the search hits
      SearchHits hits = searchResponse.getHits();
      return hits.getTotalHits().value;
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public long getTotalRecommenderCount() throws CedarProcessingException {
    try {
      // Create the search request
      SearchRequest searchRequest = new SearchRequest(config.getIndexes().getRulesIndex().getName());
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(QueryBuilders.matchAllQuery());
      searchSourceBuilder.size(0); // We only need the count, no hits
      searchRequest.source(searchSourceBuilder);

      // Execute the search request
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      // Process the search hits
      return searchResponse.getHits().getTotalHits().value;
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

}
