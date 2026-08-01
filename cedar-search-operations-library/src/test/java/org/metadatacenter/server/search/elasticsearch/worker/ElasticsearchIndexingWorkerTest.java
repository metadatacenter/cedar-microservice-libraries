package org.metadatacenter.server.search.elasticsearch.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
