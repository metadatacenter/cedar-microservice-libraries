package org.metadatacenter.server.search.elasticsearch.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarUntypedFilesystemResourceId;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.util.json.JsonMapper;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchIndexingWorkerTest {

  private RestHighLevelClient client;
  private ElasticsearchIndexingWorker worker;

  @BeforeEach
  void setUp() {
    client = mock(RestHighLevelClient.class);
    worker = new ElasticsearchIndexingWorker("cedar-new-index", client);
  }

  @Test
  void successfulBatchSendsEveryConcreteIndexDocument() throws Exception {
    BulkResponse response = mock(BulkResponse.class);
    when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenReturn(response);
    List<IndexingDocumentDocument> documents = List.of(
        new IndexingDocumentDocument("resource-1"),
        new IndexingDocumentDocument("resource-2"));

    worker.addBatch(documents);

    ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
    verify(client).bulk(request.capture(), any(RequestOptions.class));
    assertEquals(2, request.getValue().numberOfActions());
  }

  @Test
  void itemLevelBulkFailureEscapesSoIncompleteIndexCannotBePromoted() throws Exception {
    BulkResponse response = mock(BulkResponse.class);
    when(response.hasFailures()).thenReturn(true);
    when(response.buildFailureMessage()).thenReturn("resource-2 rejected");
    when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenReturn(response);

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> worker.addBatch(List.of(new IndexingDocumentDocument("resource-1"))));

    assertTrue(error.getMessage().contains("resource-2 rejected"));
  }

  @Test
  void backendIoFailureEscapesSoIncompleteIndexCannotBePromoted() throws Exception {
    when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenThrow(new IOException("offline"));

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> worker.addBatch(List.of(new IndexingDocumentDocument("resource-1"))));

    assertTrue(error.getMessage().contains("Error executing bulk request"));
  }

  @Test
  void emptyOrAbsentBatchDoesNotIssueInvalidBackendRequest() throws Exception {
    assertDoesNotThrow(() -> worker.addBatch(List.of()));
    assertDoesNotThrow(() -> worker.addBatch(null));
    verify(client, never()).bulk(any(BulkRequest.class), any(RequestOptions.class));
  }

  @Test
  void batchIdentifiesEachDocumentByItsCedarIdSoARepeatIsNotADuplicate() throws Exception {
    BulkResponse response = mock(BulkResponse.class);
    when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenReturn(response);

    worker.addBatch(List.of(new IndexingDocumentDocument("resource-1")));

    ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
    verify(client).bulk(request.capture(), any(RequestOptions.class));
    DocWriteRequest<?> indexed = request.getValue().requests().get(0);
    assertEquals("resource-1", indexed.id());
  }

  @Test
  void indexingAResourceAddressesItsOwnDocumentSoAReindexReplacesInPlace() throws Exception {
    stubIndexResponse(RestStatus.CREATED, "resource-1");

    worker.addToIndex(JsonMapper.MAPPER.createObjectNode(), "resource-1");

    ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
    verify(client).index(request.capture(), any(RequestOptions.class));
    assertEquals("resource-1", request.getValue().id());
  }

  /**
   * Replacing an existing document answers OK rather than CREATED. Treating that as a failure
   * would make every re-index of a resource throw.
   */
  @Test
  void replacingAnExistingDocumentIsSuccessNotFailure() throws Exception {
    stubIndexResponse(RestStatus.OK, "resource-1");

    assertDoesNotThrow(() -> worker.addToIndex(JsonMapper.MAPPER.createObjectNode(), "resource-1"));
  }

  /**
   * Rules index many documents against one template, so that path must keep letting the backend
   * generate ids. Sharing one id would collapse a template's rules into a single document.
   */
  @Test
  void indexingWithoutACedarIdLeavesTheIdToTheBackend() throws Exception {
    stubIndexResponse(RestStatus.CREATED, "generated-id");

    worker.addToIndex(JsonMapper.MAPPER.createObjectNode());

    ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
    verify(client).index(request.capture(), any(RequestOptions.class));
    assertNull(request.getValue().id());
  }

  /**
   * The defect this covers: delete-by-query only sees refreshed segments, so removing a resource
   * indexed moments earlier deleted nothing and left the document orphaned in the index for good.
   * Deleting by id is realtime and reaches it.
   */
  @Test
  void removalDeletesByIdSoADocumentTooRecentToBeSearchableIsStillReached() throws Exception {
    stubDeleteResponse(RestStatus.OK);
    stubDeleteByQueryDeleted(0);

    long removed = worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1"));

    ArgumentCaptor<DeleteRequest> request = ArgumentCaptor.forClass(DeleteRequest.class);
    verify(client).delete(request.capture(), any(RequestOptions.class));
    assertEquals("resource-1", request.getValue().id());
    assertEquals(1, removed, "the delete by id counts, even though the query matched nothing");
  }

  /** Documents an older build wrote under a generated id are still reachable only by query. */
  @Test
  void removalAlsoSweepsDocumentsHeldUnderAGeneratedId() throws Exception {
    stubDeleteResponse(RestStatus.NOT_FOUND);
    stubDeleteByQueryDeleted(3);

    long removed = worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1"));

    assertEquals(3, removed, "a resource absent under its own id is still swept by the query");
  }

  @Test
  void removingSomethingTheIndexNeverHeldRemovesNothing() throws Exception {
    stubDeleteResponse(RestStatus.NOT_FOUND);
    stubDeleteByQueryDeleted(0);

    assertEquals(0, worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1")));
  }

  private void stubIndexResponse(RestStatus status, String id) throws IOException {
    IndexResponse response = mock(IndexResponse.class);
    when(response.status()).thenReturn(status);
    when(response.getId()).thenReturn(id);
    when(client.index(any(IndexRequest.class), any(RequestOptions.class))).thenReturn(response);
  }

  private void stubDeleteResponse(RestStatus status) throws IOException {
    DeleteResponse response = mock(DeleteResponse.class);
    when(response.status()).thenReturn(status);
    when(client.delete(any(DeleteRequest.class), any(RequestOptions.class))).thenReturn(response);
  }

  private void stubDeleteByQueryDeleted(long deleted) throws IOException {
    BulkByScrollResponse response = mock(BulkByScrollResponse.class);
    when(response.getDeleted()).thenReturn(deleted);
    when(client.deleteByQuery(any(DeleteByQueryRequest.class), any(RequestOptions.class))).thenReturn(response);
  }
}
