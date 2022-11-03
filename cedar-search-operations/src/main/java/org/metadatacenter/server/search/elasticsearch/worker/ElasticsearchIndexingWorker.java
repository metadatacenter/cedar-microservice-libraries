package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.index.reindex.DeleteByQueryRequestBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.search.IndexedDocumentType;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;

public class ElasticsearchIndexingWorker {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexingWorker.class);

  private final Client client;
  private final String indexName;
  private final String documentType;

  public ElasticsearchIndexingWorker(String indexName, Client client) {
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
          IndexRequestBuilder indexRequestBuilder = client.prepareIndex(indexName, documentType)
              .setSource(JsonMapper.MAPPER.writeValueAsString(json), XContentType.JSON);
          IndexResponse response = indexRequestBuilder.get();
          if (response.status() == RestStatus.CREATED) {
            log.debug("The " + documentType + " has been indexed");
            again = false;
            newId = new IndexedDocumentId(response.getId());
          } else {
            throw new CedarProcessingException("Failed to index " + documentType);
          }
        } catch (NoNodeAvailableException e) {
          if (count++ > maxAttempts) {
            throw e;
          }
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
    log.debug("Removing " + documentType + " cid:" + resourceId + " from the " + indexName + " index");
    try {
      // Get resources by artifact id
      // TODO: note that this search query will retrieve only 10 results by default, so the maximum number
      // of documents that will be removed will be 10. Consider using the "delete by query" API
      SearchResponse responseSearch = client.prepareSearch(indexName)
          .setQuery(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, resourceId.getId()))
          .execute().actionGet();

      long removedCount = 0;
      // Delete by Elasticsearch id
      for (SearchHit hit : responseSearch.getHits()) {
        removeFromIndex(hit.getId());
        removedCount++;
      }
      if (removedCount == 0) {
        log.error("The " + documentType + " cid:" + resourceId.getId() + " was not removed from the " + indexName +
            " index");
      } else {
        log.debug("Removed " + removedCount + " documents of type " + documentType + " cid:" + resourceId.getId() +
            " from the " + indexName + " " +
            "index");
      }
      return removedCount;
    } catch (Exception e) {
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
      // Use "delete by query" to delete all documents with fieldName = fieldValue
      BulkByScrollResponse response = new DeleteByQueryRequestBuilder(client, DeleteByQueryAction.INSTANCE)
          .filter(QueryBuilders.matchQuery(fieldName, fieldValue)).source(indexName)
          .get();
      long removedCount = response.getDeleted();
      if (removedCount == 0) {
        log.error("No documents have been removed from the " + indexName + " index");
      } else {
        log.debug("Removed " + removedCount + " documents from the " + indexName + " index");
      }
      return removedCount;
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }

  public void removeFromIndex(String documentId) throws CedarProcessingException {
    DeleteRequestBuilder deleteRequestBuilder = client.prepareDelete(indexName, documentType, documentId);
    DeleteResponse responseDelete = deleteRequestBuilder.execute().actionGet();
    if (responseDelete.status() != RestStatus.OK) {
      throw new CedarProcessingException("Failed to remove " + documentType
          + " _id:" + documentId + " from the " + indexName + " index");
    } else {
      log.debug("The " + documentType + " " + documentId + " has been removed from the " + indexName + " index");
    }
  }

  public void addBatch(List<IndexingDocumentDocument> currentBatch) {
    if (currentBatch != null) {
      BulkRequestBuilder bulkRequest = client.prepareBulk();

      for (IndexingDocumentDocument ir : currentBatch) {
        JsonNode jsonResource = JsonMapper.MAPPER.convertValue(ir, JsonNode.class);

        try {
          bulkRequest.add(client.prepareIndex(indexName, documentType)
              .setSource(JsonMapper.MAPPER.writeValueAsString(jsonResource), XContentType.JSON));
        } catch (JsonProcessingException e) {
          log.error("Error while serializing indexing document", e);
        }
      }

      BulkResponse bulkResponse = bulkRequest.get();
      if (bulkResponse.hasFailures()) {
        // process failures by iterating through each bulk response item
        log.error("Failure when processing bulk request:");
        log.error(bulkResponse.buildFailureMessage());
      }
    }
  }
}
