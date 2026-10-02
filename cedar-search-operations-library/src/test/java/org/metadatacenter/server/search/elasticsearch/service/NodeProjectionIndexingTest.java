package org.metadatacenter.server.search.elasticsearch.service;

import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.search.util.IndexRebuildRegistry;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NodeProjectionIndexingTest {
  @Test void failedRebuildMirrorIsNotAcknowledgedAndRecoveryRepeatsBothIdempotentWrites() throws Exception {
    var client=mock(RestHighLevelClient.class);
    var config=mock(CedarConfig.class,RETURNS_DEEP_STUBS);
    when(config.getServers().getArtifact().getBase()).thenReturn("http://127.0.0.1:1");
    when(client.count(any(org.opensearch.client.core.CountRequest.class),any(RequestOptions.class)))
        .thenReturn(mock(org.opensearch.client.core.CountResponse.class));
    var service=spy(new NodeIndexingService(config,"live",client));
    var artifact=new FolderServerTemplate(); artifact.setId("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111");
    doReturn(new IndexingDocumentDocument("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111"))
        .when(service).createIndexDocument(same(artifact),isNull(),isNull(),isNull(),eq(false));
    var reply=mock(IndexResponse.class);
    when(reply.status()).thenReturn(RestStatus.OK); when(reply.getId()).thenReturn("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111");
    var unavailable=new AtomicBoolean(true);
    when(client.index(any(IndexRequest.class),any(RequestOptions.class))).thenAnswer(call -> {
      IndexRequest request=call.getArgument(0);
      assertEquals("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111",request.id());
      if (request.index().equals("rebuild") && unavailable.get()) throw new IOException("mirror unavailable");
      return reply;
    });
    try(var registry=mockStatic(IndexRebuildRegistry.class)) {
      registry.when(IndexRebuildRegistry::inProgressIndex).thenReturn(Optional.of("rebuild"));
      assertThrows(CedarProcessingException.class,() -> service.indexDocument(artifact,null,null,null,false,true));
      verify(client,times(2)).index(any(IndexRequest.class),any(RequestOptions.class));
      registry.verify(() -> IndexRebuildRegistry.recordLiveWrite("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111"),never());
      unavailable.set(false);
      assertNotNull(service.indexDocument(artifact,null,null,null,false,true));
      verify(client,times(4)).index(any(IndexRequest.class),any(RequestOptions.class));
      registry.verify(() -> IndexRebuildRegistry.recordLiveWrite("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111"));
    }
  }
}
