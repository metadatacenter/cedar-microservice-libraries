package org.metadatacenter.server.search.elasticsearch.service;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.server.search.IndexedDocumentId;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;

public class AbstractSearchingService {

  protected IndexedDocumentId getByCedarId(Client client, String resourceId, String indexName, String documentType)
      throws CedarProcessingException {
    try {
      // Get resources by artifact id
      SearchResponse responseSearch = client.prepareSearch(indexName)
          .setQuery(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, resourceId))
          .execute().actionGet();
      for (SearchHit hit : responseSearch.getHits()) {
        if (hit != null) {
          return new IndexedDocumentId(hit.getId());
        }
      }
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
    return null;
  }
}
