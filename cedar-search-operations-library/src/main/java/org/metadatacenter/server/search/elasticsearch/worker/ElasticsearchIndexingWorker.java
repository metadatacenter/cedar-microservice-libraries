package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;

public class ElasticsearchIndexingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexingWorker.class);

  private final RestHighLevelClient client;
  private final String indexName;
  private final String documentType;

  public ElasticsearchIndexingWorker(String indexName, RestHighLevelClient client) {
    this.client = client;
    this.indexName = indexName;
    this.documentType = IndexedDocumentType.DOC.getValue();
  }

  public IndexedDocumentId addToIndex(JsonNode json) throws CedarProcessingException {
    IndexedDocumentId newId = null;
    try {
      boolean again = true;
      int maxAttempts = 20;
      int count = 0;
      while (again) {
        try {
          IndexRequest indexRequest = new IndexRequest(indexName)
              .source(JsonMapper.MAPPER.writeValueAsString(json), XContentType.JSON);
          IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
          if (response.status() == RestStatus.CREATED) {
            log.debug("The " + documentType + " has been indexed");
            again = false;
            newId = new IndexedDocumentId(response.getId());
          } else {
            throw new CedarProcessingException("Failed to index " + documentType);
          }
        } catch (IOException e) {
          if (++count > maxAttempts) {
            throw new CedarProcessingException("Max attempts reached while indexing " + documentType, e);
          }
          log.warn("NoNodeAvailableException occurred, retrying... Attempt: " + count, e);
        }
      }
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
    return newId;
  }

  /**
   * Removes from the index all documents that match a given CEDAR artifact id
   *
   * @param resourceId
   * @return
   * @throws CedarProcessingException
   */
  public long removeAllFromIndex(CedarFilesystemResourceId resourceId) throws CedarProcessingException {
    log.debug("Removing " + documentType + " cid:" + resourceId.getId() + " from the " + indexName + " index");
    try {
      // Create the delete by query request
      DeleteByQueryRequest request = new DeleteByQueryRequest(indexName);
      request.setQuery(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, resourceId.getId()));

      // Execute the delete by query request
      BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);

      long removedCount = response.getDeleted();
      if (removedCount == 0) {
        // Expected, self-healing transient: delete-by-query matches on cid, and a doc
        // indexed moments earlier may not be refreshed into the searchable index yet
        // (OpenSearch ~1s refresh interval), so a remove during a rapid create→update
        // finds nothing. The caller decides whether 0-removed is a real failure — the
        // retry overload in NodeIndexingService treats it as retryable — so this is a
        // warning here, not an error.
        log.warn("The " + documentType + " cid:" + resourceId.getId() + " was not removed from the " + indexName + " index");
      } else {
        log.debug("Removed " + removedCount + " documents of type " + documentType + " cid:" + resourceId.getId() + " from the " + indexName + " index");
      }
      return removedCount;
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  /**
   * Removes from the index all documents with fieldName = fieldValue
   *
   * @param fieldName
   * @param fieldValue
   * @return
   * @throws CedarProcessingException
   */
  public long removeAllFromIndex(String fieldName, String fieldValue) throws CedarProcessingException {
    log.debug("Removing from the " + indexName + " index the documents with " + fieldName + "=" + fieldValue);
    try {
      // Create the delete by query request
      DeleteByQueryRequest request = new DeleteByQueryRequest(indexName);
      request.setQuery(QueryBuilders.matchQuery(fieldName, fieldValue));

      // Execute the delete by query request
      BulkByScrollResponse response = client.deleteByQuery(request, RequestOptions.DEFAULT);

      long removedCount = response.getDeleted();
      if (removedCount == 0) {
        log.error("No documents have been removed from the " + indexName + " index");
      } else {
        log.debug("Removed " + removedCount + " documents from the " + indexName + " index");
      }
      return removedCount;
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public void removeFromIndex(String documentId) throws CedarProcessingException {
    try {
      DeleteRequest deleteRequest = new DeleteRequest(indexName, documentId);
      DeleteResponse deleteResponse = client.delete(deleteRequest, RequestOptions.DEFAULT);
      if (deleteResponse.status() != RestStatus.OK) {
        throw new CedarProcessingException("Failed to remove " + documentType + " _id:" + documentId + " from the " + indexName + " index");
      } else {
        log.debug("The " + documentType + " " + documentId + " has been removed from the " + indexName + " index");
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error removing " + documentType + " _id:" + documentId + " from the " + indexName + " index", e);
    }
  }

  public void addBatch(List<IndexingDocumentDocument> currentBatch) throws CedarProcessingException {
    if (currentBatch == null || currentBatch.isEmpty()) {
      return;
    }
    BulkRequest bulkRequest = new BulkRequest();

    for (IndexingDocumentDocument ir : currentBatch) {
      JsonNode jsonResource = JsonMapper.MAPPER.convertValue(ir, JsonNode.class);
      try {
        IndexRequest indexRequest = new IndexRequest(indexName)
            .source(JsonMapper.MAPPER.writeValueAsString(jsonResource), XContentType.JSON);
        bulkRequest.add(indexRequest);
      } catch (JsonProcessingException e) {
        throw new CedarProcessingException("Error while serializing indexing document", e);
      }
    }

    try {
      BulkResponse bulkResponse = client.bulk(bulkRequest, RequestOptions.DEFAULT);
      if (bulkResponse.hasFailures()) {
        throw new CedarProcessingException("Failure when processing bulk request: " +
            bulkResponse.buildFailureMessage());
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error executing bulk request", e);
    }
  }
}
