package org.metadatacenter.server.search.elasticsearch.service;

import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.io.IOException;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;

public class AbstractSearchingService {

  protected IndexedDocumentId getByCedarId(RestHighLevelClient client, String resourceId, String indexName, String documentType)
      throws CedarProcessingException {
    try {
      // Create the search request
      SearchRequest searchRequest = new SearchRequest(indexName);
      SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, resourceId));
      searchRequest.source(searchSourceBuilder);

      // Execute the search request
      SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

      // Process the search hits
      for (SearchHit hit : searchResponse.getHits().getHits()) {
        if (hit != null) {
          return new IndexedDocumentId(hit.getId());
        }
      }
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
    return null;
  }
}
