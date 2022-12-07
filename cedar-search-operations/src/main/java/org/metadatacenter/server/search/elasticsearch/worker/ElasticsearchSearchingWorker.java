package org.metadatacenter.server.search.elasticsearch.worker;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElasticsearchSearchingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchingWorker.class);

  private final Client client;
  private final String indexName;
  private final OpensearchConfig config;
  private final TimeValue keepAlive;

  public ElasticsearchSearchingWorker(OpensearchConfig config, Client client) {
    this.config = config;
    this.client = client;
    this.indexName = config.getIndexes().getSearchIndex().getName();
    this.keepAlive = new TimeValue(config.getScrollKeepAlive());
  }

  // Retrieve all values for a fieldName. Dot notation is allowed (e.g. info.@id)
  public List<String> findAllValuesForField(String fieldName) throws CedarProcessingException {
    QueryBuilder qb = QueryBuilders.matchAllQuery();
    return findAllValuesForField(fieldName, qb);
  }

  public List<String> findAllValuesForField(String fieldName, QueryBuilder queryBuilder) {
    List<String> fieldValues = new ArrayList<>();
    SearchRequestBuilder searchRequest = client.prepareSearch(indexName)
        .setFetchSource(new String[]{fieldName}, null)
        .setScroll(keepAlive).setQuery(queryBuilder).setSize(config.getSize());
    SearchResponse response = searchRequest.execute().actionGet();
    // Scroll until no hits are returned
    do {
      for (SearchHit hit : response.getHits().getHits()) {
        Map<String, Object> f = hit.getSourceAsMap();
        String[] pathFragments = fieldName.split("\\.");
        for (int i = 0; i < pathFragments.length - 1; i++) {
          f = (Map<String, Object>) f.get(pathFragments[0]);
        }
        String fieldValue = (String) f.get(pathFragments[pathFragments.length - 1]);
        fieldValues.add(fieldValue);
      }
      response = client.prepareSearchScroll(response.getScrollId()).setScroll(keepAlive)
          .execute().actionGet();
      // Break condition: No hits are returned
    } while (response.getHits().getHits().length != 0);
    return fieldValues;
  }
}
