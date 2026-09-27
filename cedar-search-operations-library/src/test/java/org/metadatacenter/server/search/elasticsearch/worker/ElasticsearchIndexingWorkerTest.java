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
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;
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

    worker.addToIndex(JsonMapper.STRICT_MAPPER.createObjectNode(), "resource-1");

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

    assertDoesNotThrow(() -> worker.addToIndex(JsonMapper.STRICT_MAPPER.createObjectNode(), "resource-1"));
  }

  /**
   * Rules index many documents against one template, so that path must keep letting the backend
   * generate ids. Sharing one id would collapse a template's rules into a single document.
   */
  @Test
  void indexingWithoutACedarIdLeavesTheIdToTheBackend() throws Exception {
    stubIndexResponse(RestStatus.CREATED, "generated-id");

    worker.addToIndex(JsonMapper.STRICT_MAPPER.createObjectNode());

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
    verify(client, never()).deleteByQuery(any(), any());
  }

  /** Documents an older build wrote under a generated id are still reachable only by query. */
  @Test
  void removalAlsoSweepsDocumentsHeldUnderAGeneratedId() throws Exception {
    stubDeleteResponse(RestStatus.NOT_FOUND);
    stubDeleteByQueryDeleted(3);

    long removed = worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1"));

    assertEquals(3, removed, "a resource absent under its own id is still swept by the query");
    ArgumentCaptor<DeleteByQueryRequest> request = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
    verify(client).deleteByQuery(request.capture(), any());
    var query = JsonMapper.STRICT_MAPPER.readTree(request.getValue().getSearchRequest().source().query().toString());
    assertEquals("resource-1", query.path("bool").path("must_not").get(0)
        .path("ids").path("values").get(0).asText(), "legacy cleanup must exclude the current document");
  }

  @Test
  void removingSomethingTheIndexNeverHeldRemovesNothing() throws Exception {
    stubDeleteResponse(RestStatus.NOT_FOUND);
    stubDeleteByQueryDeleted(0);

    assertEquals(0, worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1")));
  }

  @Test
  void failedLegacyInspectionEscapesForRetryInsteadOfSkippingOldPermissions() throws Exception {
    stubDeleteResponse(RestStatus.OK);
    CountResponse response = mock(CountResponse.class);
    when(response.getFailedShards()).thenReturn(1);
    when(client.count(any(), any())).thenReturn(response);
    assertThrows(CedarProcessingException.class,
        () -> worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1")));
    verify(client, never()).deleteByQuery(any(), any());
  }

  @Test
  void incompleteLegacyRemovalEscapesForRetry() throws Exception {
    stubDeleteResponse(RestStatus.OK);
    stubDeleteByQueryDeleted(2);
    BulkByScrollResponse response = mock(BulkByScrollResponse.class);
    when(response.isTimedOut()).thenReturn(true);
    when(client.deleteByQuery(any(), any())).thenReturn(response);
    assertThrows(CedarProcessingException.class,
        () -> worker.removeAllFromIndex(CedarUntypedFilesystemResourceId.build("resource-1")));
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
    CountResponse count = mock(CountResponse.class);
    when(count.getCount()).thenReturn(deleted);
    when(client.count(any(CountRequest.class), any(RequestOptions.class))).thenReturn(count);
    BulkByScrollResponse response = mock(BulkByScrollResponse.class);
    when(response.getDeleted()).thenReturn(deleted);
    when(client.deleteByQuery(any(DeleteByQueryRequest.class), any(RequestOptions.class))).thenReturn(response);
  }
  @Test
  void permissionBulkRequiresEveryResultAndOnlyTreatsNotFoundAsMissing() throws Exception {
    var response = mock(BulkResponse.class);
    var success = mock(org.opensearch.action.bulk.BulkItemResponse.class);
    var missing = mock(org.opensearch.action.bulk.BulkItemResponse.class);
    when(missing.isFailed()).thenReturn(true);
    when(missing.status()).thenReturn(RestStatus.NOT_FOUND);
    when(missing.getId()).thenReturn("missing");
    when(response.getItems()).thenReturn(new org.opensearch.action.bulk.BulkItemResponse[]{success, missing});
    when(client.bulk(any(BulkRequest.class), any(RequestOptions.class))).thenReturn(response);
    var patch = JsonMapper.STRICT_MAPPER.createObjectNode().put("summaryText", "summary");
    var patches = java.util.Map.<String, com.fasterxml.jackson.databind.JsonNode>of("present", patch, "missing", patch);
    assertEquals(java.util.Set.of("missing"), worker.updatePermissionProjections(patches));
    var sent = ArgumentCaptor.forClass(BulkRequest.class);
    verify(client).bulk(sent.capture(), any(RequestOptions.class));
    assertEquals(org.opensearch.action.support.WriteRequest.RefreshPolicy.NONE, sent.getValue().getRefreshPolicy());
    when(missing.status()).thenReturn(RestStatus.BAD_REQUEST);
    assertThrows(CedarProcessingException.class, () -> worker.updatePermissionProjections(patches));
    when(response.getItems()).thenReturn(new org.opensearch.action.bulk.BulkItemResponse[]{success});
    assertThrows(CedarProcessingException.class, () -> worker.updatePermissionProjections(patches));
  }

  @Test
  void singlePermissionUpdateIsSearchableBeforeItIsAcknowledged() throws Exception {
    var response = mock(org.opensearch.action.update.UpdateResponse.class);
    when(response.status()).thenReturn(RestStatus.OK);
    when(client.update(any(), any())).thenReturn(response);
    assertTrue(worker.updatePermissionProjection(JsonMapper.STRICT_MAPPER.createObjectNode(), "resource"));
    var sent = ArgumentCaptor.forClass(org.opensearch.action.update.UpdateRequest.class);
    verify(client).update(sent.capture(), any(RequestOptions.class));
    assertEquals(org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE, sent.getValue().getRefreshPolicy());
  }

}
