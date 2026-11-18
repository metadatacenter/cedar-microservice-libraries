package org.metadatacenter.server.search.elasticsearch.service;

import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.server.FolderServiceSession;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A permission projection that rebuilds a document can race a delete: the projection reads the
 * resource from the graph, the delete removes the node and then the document, and the projection then
 * writes the document back. Reading the graph again after the write settles it.
 */
class NodeDeletedDuringWriteTest {
  private static FolderServerTemplate template() {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId("https://repo.metadatacenter.org/templates/55555555-5555-5555-5555-555555555555");
    template.setName("Deleted while projected");
    return template;
  }

  private static NodeIndexingService service() throws Exception {
    var config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(config.getServers().getArtifact().getBase()).thenReturn("http://127.0.0.1:1");
    var client = mock(RestHighLevelClient.class);
    when(client.count(any(CountRequest.class), any(RequestOptions.class))).thenReturn(mock(CountResponse.class));
    var service = spy(new NodeIndexingService(config, "live", client));
    doReturn(1L).when(service).removeDocumentFromIndex(any());
    return service;
  }

  @Test
  void aDocumentWrittenForAResourceTheGraphNoLongerHoldsIsRemoved() throws Exception {
    var template = template();
    var service = service();
    var folders = mock(FolderServiceSession.class);
    when(folders.findResourceById(template.getResourceId())).thenReturn(null);
    var services = mock(CedarDataServices.class);
    when(services.getFolderServiceSession(any())).thenReturn(folders);
    try (var dataServices = mockStatic(CedarDataServices.class)) {
      dataServices.when(CedarDataServices::getInstance).thenReturn(services);
      service.removeIfDeleted(template, null);
    }
    verify(service).removeDocumentFromIndex(template.getResourceId());
  }

  @Test
  void aDocumentWrittenForAResourceThatStillExistsIsKept() throws Exception {
    var template = template();
    var service = service();
    var folders = mock(FolderServiceSession.class);
    when(folders.findResourceById(template.getResourceId())).thenReturn(template);
    var services = mock(CedarDataServices.class);
    when(services.getFolderServiceSession(any())).thenReturn(folders);
    try (var dataServices = mockStatic(CedarDataServices.class)) {
      dataServices.when(CedarDataServices::getInstance).thenReturn(services);
      service.removeIfDeleted(template, null);
    }
    verify(service, never()).removeDocumentFromIndex(any());
  }
}
