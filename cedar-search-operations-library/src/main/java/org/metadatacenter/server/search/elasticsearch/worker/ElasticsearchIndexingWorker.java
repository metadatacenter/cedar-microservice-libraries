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

  /**
   * Indexes a document under a backend-generated id. Every call creates a new document, so this
   * is only correct where a CEDAR id legitimately maps to many documents, as it does for rules.
   * To index one document per resource, use {@link #addToIndex(JsonNode, String)}.
   */
  public IndexedDocumentId addToIndex(JsonNode json) throws CedarProcessingException {
    return addToIndex(json, null);
  }

  /**
   * Indexes a document under a caller-chosen id, replacing any document already held under that
   * id. Passing the CEDAR id makes indexing idempotent: a resource occupies exactly one document
   * however many times it is indexed, and a re-index needs no prior removal to avoid a duplicate.
   * A null documentId falls back to a backend-generated id.
   */
  public IndexedDocumentId addToIndex(JsonNode json, String documentId) throws CedarProcessingException {
    IndexedDocumentId newId = null;
    try {
      boolean again = true;
      int maxAttempts = 20;
      int count = 0;
      while (again) {
        try {
          IndexRequest indexRequest = new IndexRequest(indexName)
              .source(JsonMapper.MAPPER.writeValueAsString(json), XContentType.JSON);
          if (documentId != null) {
            indexRequest.id(documentId);
          }
          IndexResponse response = client.index(indexRequest, RequestOptions.DEFAULT);
          // CREATED is a first write, OK an overwrite of an existing document; both leave the
          // index holding exactly what was asked for
          if (response.status() == RestStatus.CREATED || response.status() == RestStatus.OK) {
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
   * Removes from the index every document held for a given CEDAR resource.
   * <p>
   * Removal proceeds in two steps because the index can hold a resource under two kinds of id.
   * A document indexed through {@link #addToIndex(JsonNode, String)} sits under the CEDAR id, and
   * a delete by that id is a realtime operation: it reaches a document written moments earlier,
   * before any refresh has made it searchable. A document written under a backend-generated id —
   * by an older build, or by the batch path — is reachable only by a query over the cid field,
   * which sees just the refreshed segments. Doing both removes the resource whichever way it was
   * indexed, and confines the refresh race to documents that no current write path produces.
   */
  public long removeAllFromIndex(CedarFilesystemResourceId resourceId) throws CedarProcessingException {
    String cedarId = resourceId.getId();
    log.debug("Removing " + documentType + " cid:" + cedarId + " from the " + indexName + " index");
    try {
      long removedCount = 0;

      DeleteRequest byIdRequest = new DeleteRequest(indexName, cedarId);
      DeleteResponse byIdResponse = client.delete(byIdRequest, RequestOptions.DEFAULT);
      if (byIdResponse.status() == RestStatus.OK) {
        removedCount++;
      }

      DeleteByQueryRequest byQueryRequest = new DeleteByQueryRequest(indexName);
      byQueryRequest.setQuery(QueryBuilders.matchQuery(DOCUMENT_CEDAR_ID, cedarId));
      BulkByScrollResponse byQueryResponse = client.deleteByQuery(byQueryRequest, RequestOptions.DEFAULT);
      removedCount += byQueryResponse.getDeleted();

      if (removedCount == 0) {
        // Either the resource was never indexed, or it is held under a backend-generated id
        // that is not yet searchable. The caller decides whether that is a real failure — the
        // retry overload in NodeIndexingService treats it as retryable — so this is a warning
        // here, not an error.
        log.warn("The " + documentType + " cid:" + cedarId + " was not removed from the " + indexName + " index");
      } else {
        log.debug("Removed " + removedCount + " documents of type " + documentType + " cid:" + cedarId + " from the " + indexName + " index");
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
        // Index under the CEDAR id, so a resource that appears twice in a regeneration run
        // ends up as one document rather than two
        if (ir.getCid() != null) {
          indexRequest.id(ir.getCid());
        }
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
