package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUntypedFilesystemResourceId;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchPermissionExecutorServiceTest {

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
