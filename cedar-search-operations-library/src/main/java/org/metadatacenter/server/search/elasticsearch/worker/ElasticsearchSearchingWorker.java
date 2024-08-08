package org.metadatacenter.server.search.elasticsearch.worker;

import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElasticsearchSearchingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchSearchingWorker.class);

  private final RestHighLevelClient client;
  private final String indexName;
  private final OpensearchConfig config;
  private final TimeValue keepAlive;

  public ElasticsearchSearchingWorker(OpensearchConfig config, RestHighLevelClient client) {
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

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .fetchSource(new FetchSourceContext(true, new String[]{fieldName}, null))
        .query(queryBuilder)
        .size(config.getSize());

    SearchRequest searchRequest = new SearchRequest(indexName)
        .source(searchSourceBuilder)
        .scroll(keepAlive);

    try {
      SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

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

        String scrollId = response.getScrollId();
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(keepAlive);
        response = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      } while (response.getHits().getHits().length != 0);
    } catch (IOException e) {
      log.error("Error while searching all values", e);
    }

    return fieldValues;
  }
}
