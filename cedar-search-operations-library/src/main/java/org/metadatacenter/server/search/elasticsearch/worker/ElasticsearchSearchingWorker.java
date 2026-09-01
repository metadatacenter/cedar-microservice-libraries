package org.metadatacenter.server.search.elasticsearch.worker;

import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarProcessingException;
import org.opensearch.OpenSearchException;
import org.opensearch.action.search.ClearScrollRequest;
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
    this.keepAlive = new TimeValue(config.getSearchContextKeepAlive());
  }

  // Retrieve all values for a fieldName. Dot notation is allowed (e.g. info.@id)
  public List<String> findAllValuesForField(String fieldName) throws CedarProcessingException {
    QueryBuilder qb = QueryBuilders.matchAllQuery();
    return findAllValuesForField(fieldName, qb);
  }

  public List<String> findAllValuesForField(String fieldName, QueryBuilder queryBuilder) throws CedarProcessingException {
    List<String> fieldValues = new ArrayList<>();

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .fetchSource(new FetchSourceContext(true, new String[]{fieldName}, null))
        .query(queryBuilder)
        .size(config.getSize());

    SearchRequest searchRequest = new SearchRequest(indexName)
        .source(searchSourceBuilder)
        .scroll(keepAlive);

    // The scroll id is held across the loop so the context can be released whichever way the read ends.
    // OpenSearch may answer a page with a new id, so the release targets the one seen last.
    String scrollId = null;
    try {
      SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);

      while (true) {
        scrollId = response.getScrollId();
        SearchHit[] hits = response.getHits().getHits();
        if (hits.length == 0) {
          break;
        }
        for (SearchHit hit : hits) {
          String fieldValue = getStringValue(hit.getSourceAsMap(), fieldName);
          if (fieldValue != null) {
            fieldValues.add(fieldValue);
          }
        }

        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId).scroll(keepAlive);
        response = client.scroll(scrollRequest, RequestOptions.DEFAULT);
      }
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("OpenSearch is unavailable", e);
    } finally {
      clearScroll(scrollId);
    }

    return fieldValues;
  }

  private void clearScroll(String scrollId) {
    if (scrollId == null) {
      return;
    }
    ClearScrollRequest request = new ClearScrollRequest();
    request.addScrollId(scrollId);
    try {
      client.clearScroll(request, RequestOptions.DEFAULT);
    } catch (IOException | OpenSearchException e) {
      // The context expires on its own, and a failure here must not replace the outcome of the read.
      log.warn("Unable to clear the OpenSearch scroll context", e);
    }
  }

  private String getStringValue(Map<String, Object> source, String fieldName) {
    Object current = source;
    for (String pathFragment : fieldName.split("\\.")) {
      if (!(current instanceof Map<?, ?> currentMap)) {
        return null;
      }
      current = currentMap.get(pathFragment);
    }
    return current instanceof String ? (String) current : null;
  }
}
