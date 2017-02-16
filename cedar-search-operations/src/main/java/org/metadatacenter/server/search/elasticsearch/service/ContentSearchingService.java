package org.metadatacenter.server.search.elasticsearch.service;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.search.elasticsearch.document.IndexingDocumentContent;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchPermissionEnabledContentSearchingWorker;
import org.metadatacenter.server.search.elasticsearch.worker.SearchResponseResult;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContentSearchingService extends AbstractSearchingService {

  private static final Logger log = LoggerFactory.getLogger(ContentSearchingService.class);

  private ElasticsearchPermissionEnabledContentSearchingWorker searchWorker;

  ContentSearchingService(CedarConfig cedarConfig, Client client) {
    searchWorker = new ElasticsearchPermissionEnabledContentSearchingWorker(cedarConfig, client);
  }

  public FolderServerNodeListResponse search(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                             String templateId, List<String> sortList, int limit, int offset, String
                                                 absoluteUrl) throws CedarProcessingException {
    try {
      SearchResponseResult searchResult = searchWorker.search(rctx, query, resourceTypes, sortList, templateId,
          limit, offset);
      return assembleResponse(searchResult, query, resourceTypes, templateId, sortList, limit, offset, absoluteUrl);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }


  public FolderServerNodeListResponse searchDeep(CedarRequestContext rctx, String query, List<String> resourceTypes,
                                                 String templateId, List<String> sortList, int limit) throws
      CedarProcessingException {
    try {
      SearchResponseResult searchResult = searchWorker.searchDeep(rctx, query, resourceTypes, sortList, templateId,
          limit);
      return assembleResponse(searchResult, query, resourceTypes, templateId, sortList, limit, 0, null);
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  private FolderServerNodeListResponse assembleResponse(SearchResponseResult searchResult, String query, List<String>
      resourceTypes, String templateId, List<String> sortList, int limit, int offset, String absoluteUrl) {
    List<FolderServerNode> resources = new ArrayList<>();
    for (SearchHit hit : searchResult.getResponse().getHits()) {
      String hitJson = hit.sourceAsString();
      try {
        IndexingDocumentContent content = JsonMapper.MAPPER.readValue(hitJson, IndexingDocumentContent.class);
        resources.add(content.getInfo());
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

    List<CedarNodeType> nodeTypeList = new ArrayList<>();
    if (resourceTypes != null) {
      for (String rt : resourceTypes) {
        nodeTypeList.add(CedarNodeType.forValue(rt));
      }
    }

    NodeListRequest req = new NodeListRequest();
    req.setNodeTypes(nodeTypeList);
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);
    req.setQ(query);
    req.setDerivedFromId(templateId);
    response.setRequest(req);

    return response;

  }
}