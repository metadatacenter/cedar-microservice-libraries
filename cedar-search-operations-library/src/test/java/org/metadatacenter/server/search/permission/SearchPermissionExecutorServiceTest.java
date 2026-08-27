package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUntypedFilesystemResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.search.util.IndexUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchPermissionExecutorServiceTest {

  @Test
  void aTransientIndexingFailureEscapesThenConvergesWhenTheQueueRetries() throws Exception {
    String resourceId = "https://repo.metadatacenter.orgx/templates/retry-me";
    CedarUntypedFilesystemResourceId cedarResourceId = CedarUntypedFilesystemResourceId.build(resourceId);
    NodeSearchingService searching = mock(NodeSearchingService.class);
    NodeIndexingService indexing = mock(NodeIndexingService.class);
    FolderServiceSession folders = mock(FolderServiceSession.class);
    FileSystemResource resource = mock(FileSystemResource.class);
    when(resource.getType()).thenReturn(CedarResourceType.TEMPLATE);
    when(folders.findResourceById(cedarResourceId)).thenReturn(resource);
    when(indexing.indexDocument(any(), any(), any(), any()))
        .thenThrow(new CedarProcessingException("OpenSearch is unavailable"))
        .thenReturn(null);

    SearchPermissionExecutorService service = new SearchPermissionExecutorService(
        mock(IndexUtils.class), searching, indexing, folders,
        mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class));

    CedarProcessingException failure = assertThrows(CedarProcessingException.class,
        () -> service.handleEvent(new SearchPermissionQueueEvent(
            resourceId, SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED)));

    assertEquals("OpenSearch is unavailable", failure.getMessage());
    assertDoesNotThrow(() -> service.handleEvent(new SearchPermissionQueueEvent(
        resourceId, SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED)));
    verify(indexing, times(2)).removeDocumentFromIndex(cedarResourceId);
    verify(indexing, times(2)).indexDocument(any(), any(), any(), any());
  }

  @Test
  void aTransientDeletedGroupLookupFailureEscapesThenConvergesWhenRetried() throws Exception {
    String groupId = "https://repo.metadatacenter.orgx/groups/retry-me";
    String resourceId = "https://repo.metadatacenter.orgx/templates/orphan";
    NodeSearchingService searching = mock(NodeSearchingService.class);
    when(searching.findAllCedarIdsForGroup(any(CedarGroupId.class)))
        .thenThrow(new CedarProcessingException("OpenSearch is unavailable"))
        .thenReturn(List.of(resourceId));
    NodeIndexingService indexing = mock(NodeIndexingService.class);
    FolderServiceSession folders = mock(FolderServiceSession.class);
    when(folders.findResourceById(CedarUntypedFilesystemResourceId.build(resourceId))).thenReturn(null);

    SearchPermissionExecutorService service = new SearchPermissionExecutorService(
        mock(IndexUtils.class), searching, indexing, folders,
        mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class));

    CedarProcessingException failure = assertThrows(CedarProcessingException.class,
        () -> service.handleEvent(new SearchPermissionQueueEvent(
            groupId, SearchPermissionQueueEventType.GROUP_DELETED)));

    assertEquals("OpenSearch is unavailable", failure.getMessage());
    assertDoesNotThrow(() -> service.handleEvent(new SearchPermissionQueueEvent(
        groupId, SearchPermissionQueueEventType.GROUP_DELETED)));
    verify(indexing).removeDocumentFromIndex(CedarUntypedFilesystemResourceId.build(resourceId));
  }

  @Test
  void aDeletedGroupRemovesAnIndexedResourceThatNoLongerExistsInTheGraph() throws Exception {
    String resourceId = "https://repo.metadatacenter.orgx/templates/deleted";
    NodeSearchingService searching = mock(NodeSearchingService.class);
    NodeIndexingService indexing = mock(NodeIndexingService.class);
    FolderServiceSession folders = mock(FolderServiceSession.class);
    when(searching.findAllCedarIdsForGroup(any(CedarGroupId.class))).thenReturn(List.of(resourceId));
    when(folders.findResourceById(CedarUntypedFilesystemResourceId.build(resourceId))).thenReturn(null);

    SearchPermissionExecutorService service = new SearchPermissionExecutorService(
        mock(IndexUtils.class), searching, indexing, folders,
        mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class));

    service.handleEvent(new SearchPermissionQueueEvent(
        "https://repo.metadatacenter.orgx/groups/deleted", SearchPermissionQueueEventType.GROUP_DELETED));

    verify(indexing).removeDocumentFromIndex(CedarUntypedFilesystemResourceId.build(resourceId));
    verify(indexing, never()).indexDocument(any(), any(), any(), any());
  }
}
