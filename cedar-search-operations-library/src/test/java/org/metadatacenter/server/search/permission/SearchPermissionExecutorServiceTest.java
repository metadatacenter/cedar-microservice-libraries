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
import static org.mockito.Mockito.doThrow;
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
    doThrow(new CedarProcessingException("OpenSearch is unavailable")).doNothing()
        .when(indexing).updatePermissionProjection(any(), any(), any(), any());

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
    verify(indexing, never()).removeDocumentFromIndex(cedarResourceId);
    verify(indexing, times(2)).updatePermissionProjection(any(), any(), any(), any());
    verify(indexing, never()).indexDocument(any(), any(), any(), any());
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
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void descendantWorkIsBoundedAndAnEventWaitsForEveryStartedProjection(boolean fail) throws Exception {
    NodeSearchingService searching = mock(NodeSearchingService.class);
    NodeIndexingService indexing = mock(NodeIndexingService.class);
    FolderServiceSession folders = mock(FolderServiceSession.class);
    List<String> ids = java.util.stream.IntStream.range(0, 16)
        .mapToObj(i -> "https://repo.metadatacenter.orgx/templates/parallel-" + i).toList();
    when(searching.findAllCedarIdsForGroup(any())).thenReturn(ids);
    var started = new java.util.concurrent.CountDownLatch(8);
    var release = new java.util.concurrent.CountDownLatch(1);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var completed = new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.doAnswer(invocation -> {
      int call = calls.incrementAndGet();
      started.countDown();
      if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("not released");
      completed.incrementAndGet();
      if (fail && call == 1) throw new CedarProcessingException("projection failed");
      return null;
    }).when(indexing).removeDocumentFromIndex(any());
    try (SearchPermissionExecutorService service = new SearchPermissionExecutorService(
        mock(IndexUtils.class), searching, indexing, folders,
        mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class))) {
      var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        var pending = caller.submit(() -> {
          service.handleEvent(new SearchPermissionQueueEvent(
              "https://repo.metadatacenter.orgx/groups/bounded", SearchPermissionQueueEventType.GROUP_DELETED));
          return null;
        });
        org.junit.jupiter.api.Assertions.assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(8, calls.get(), "only one bounded window may run");
        org.junit.jupiter.api.Assertions.assertFalse(pending.isDone(), "the queue must not acknowledge early");
        release.countDown();
        if (fail) {
          var failure = assertThrows(java.util.concurrent.ExecutionException.class,
              () -> pending.get(5, java.util.concurrent.TimeUnit.SECONDS));
          org.junit.jupiter.api.Assertions.assertInstanceOf(CedarProcessingException.class, failure.getCause());
          assertEquals(calls.get(), completed.get(), "all started work must finish before retrying an event");
        } else {
          pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
          assertEquals(16, completed.get());
        }
      } finally {
        release.countDown();
        caller.shutdownNow();
      }
    }
  }

  @Test
  void overlappingEventsProjectEachResourceOnce() throws Exception {
    String id = "https://repo.metadatacenter.orgx/templates/overlap";
    var indexing = mock(NodeIndexingService.class);
    var folders = mock(FolderServiceSession.class);
    var child = mock(FileSystemResource.class);
    when(child.getId()).thenReturn(id);
    when(child.getResourceId()).thenReturn(CedarUntypedFilesystemResourceId.build(id));
    when(folders.findAllDescendantNodesById(any())).thenReturn(List.of(child));
    try (var service = new SearchPermissionExecutorService(mock(IndexUtils.class), mock(NodeSearchingService.class),
        indexing, folders, mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class))) {
      service.handleEvents(List.of(
          new SearchPermissionQueueEvent(id, SearchPermissionQueueEventType.RESOURCE_MOVED),
          new SearchPermissionQueueEvent(id, SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED),
          new SearchPermissionQueueEvent("https://repo.metadatacenter.orgx/folders/parent",
              SearchPermissionQueueEventType.FOLDER_PERMISSION_CHANGED)));
      verify(indexing, times(1)).removeDocumentFromIndex(any());
    }
  }

  @Test
  void aSlowProjectionDoesNotIdleTheOtherSlots() throws Exception {
    var searching = mock(NodeSearchingService.class);
    var indexing = mock(NodeIndexingService.class);
    var folders = mock(FolderServiceSession.class);
    List<String> ids = java.util.stream.IntStream.range(0, 16)
        .mapToObj(i -> "https://repo.metadatacenter.orgx/templates/rolling-" + i).toList();
    when(searching.findAllCedarIdsForGroup(any())).thenReturn(ids);
    var release = new java.util.concurrent.CountDownLatch(1);
    var others = new java.util.concurrent.CountDownLatch(15);
    org.mockito.Mockito.doAnswer(call -> {
      org.metadatacenter.id.CedarFilesystemResourceId id = call.getArgument(0);
      if (id.getId().equals(ids.get(0))) release.await();
      else others.countDown();
      return null;
    }).when(indexing).removeDocumentFromIndex(any());
    try (var service = new SearchPermissionExecutorService(mock(IndexUtils.class), searching, indexing, folders,
        mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class))) {
      var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        var pending = caller.submit(() -> {
          service.handleEvent(new SearchPermissionQueueEvent(
              "https://repo.metadatacenter.orgx/groups/rolling", SearchPermissionQueueEventType.GROUP_DELETED));
          return null;
        });
        org.junit.jupiter.api.Assertions.assertTrue(others.await(5, java.util.concurrent.TimeUnit.SECONDS),
            "the other slots must keep working while the first projection is blocked");
        org.junit.jupiter.api.Assertions.assertFalse(pending.isDone());
        release.countDown();
        pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
      } finally {
        release.countDown();
        caller.shutdownNow();
      }
    }
  }

  @Test
  void independentExistingResourcesArePreparedThenWrittenAsOneBulk() throws Exception {
    var indexing = mock(NodeIndexingService.class);
    var folders = mock(FolderServiceSession.class);
    var events = new java.util.ArrayList<SearchPermissionQueueEvent>();
    for (int i = 0; i < 3; i++) {
      String id = "https://repo.metadatacenter.orgx/templates/bulk-" + i;
      var node = mock(FileSystemResource.class);
      when(node.getId()).thenReturn(id);
      when(node.getType()).thenReturn(CedarResourceType.TEMPLATE);
      when(folders.findResourceById(org.metadatacenter.id.CedarUntypedArtifactId.build(id))).thenReturn(node);
      events.add(new SearchPermissionQueueEvent(id, SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED));
    }
    org.mockito.Mockito.when(indexing.preparePermissionProjection(any(), any(), any(), any()))
        .thenAnswer(call -> new NodeIndexingService.PermissionProjection(call.getArgument(0), null, null, null,
            org.metadatacenter.util.json.JsonMapper.STRICT_MAPPER.createObjectNode()));
    try (var service = new SearchPermissionExecutorService(mock(IndexUtils.class), mock(NodeSearchingService.class),
        indexing, folders, mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class))) {
      service.handleEvents(events);
      verify(indexing).updatePermissionProjections(org.mockito.ArgumentMatchers.argThat(batch -> batch.size() == 3));
      verify(indexing, never()).updatePermissionProjection(any(), any(), any(), any());
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void concurrentBulksRemainBoundedAndFinishBeforeAcknowledgementOrRetry(boolean fail) throws Exception {
    var indexing = mock(NodeIndexingService.class);
    var folders = mock(FolderServiceSession.class);
    var events = new java.util.ArrayList<SearchPermissionQueueEvent>();
    for (int i = 0; i < 160; i++) {
      String id = "https://repo.metadatacenter.orgx/templates/concurrent-bulk-" + i;
      var node = mock(FileSystemResource.class);
      when(node.getId()).thenReturn(id);
      when(node.getType()).thenReturn(CedarResourceType.TEMPLATE);
      when(folders.findResourceById(org.metadatacenter.id.CedarUntypedArtifactId.build(id))).thenReturn(node);
      events.add(new SearchPermissionQueueEvent(id, SearchPermissionQueueEventType.RESOURCE_PERMISSION_CHANGED));
    }
    when(indexing.preparePermissionProjection(any(), any(), any(), any()))
        .thenAnswer(call -> new NodeIndexingService.PermissionProjection(call.getArgument(0), null, null, null,
            org.metadatacenter.util.json.JsonMapper.STRICT_MAPPER.createObjectNode()));
    var entered = new java.util.concurrent.CountDownLatch(4);
    var releaseFirst = new java.util.concurrent.CountDownLatch(1);
    var releaseOthers = new java.util.concurrent.CountDownLatch(1);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var completed = new java.util.concurrent.atomic.AtomicInteger();
    var active = new java.util.concurrent.atomic.AtomicInteger();
    var maximum = new java.util.concurrent.atomic.AtomicInteger();
    var ids = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
    org.mockito.Mockito.doAnswer(call -> {
      int number = calls.getAndIncrement();
      maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
      try {
        List<NodeIndexingService.PermissionProjection> batch = call.getArgument(0);
        org.junit.jupiter.api.Assertions.assertTrue(batch.size() <= 32);
        batch.forEach(p -> org.junit.jupiter.api.Assertions.assertTrue(ids.add(p.resource().getId())));
        entered.countDown();
        if (number == 0) {
          releaseFirst.await();
          if (fail) throw new CedarProcessingException("bulk failed");
        } else releaseOthers.await();
        return null;
      } finally {
        active.decrementAndGet();
        completed.incrementAndGet();
      }
    }).when(indexing).updatePermissionProjections(any());
    try (var service = new SearchPermissionExecutorService(mock(IndexUtils.class), mock(NodeSearchingService.class),
        indexing, folders, mock(ResourcePermissionServiceSession.class), mock(CategoryServiceSession.class),
        mock(CedarRequestContext.class))) {
      var caller = java.util.concurrent.Executors.newSingleThreadExecutor();
      try {
        var pending = caller.submit(() -> { service.handleEvents(events); return null; });
        org.junit.jupiter.api.Assertions.assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        releaseFirst.countDown();
        Thread.sleep(100);
        org.junit.jupiter.api.Assertions.assertFalse(pending.isDone(), "other started writes still hold the durable batch");
        releaseOthers.countDown();
        if (fail) assertThrows(java.util.concurrent.ExecutionException.class,
            () -> pending.get(5, java.util.concurrent.TimeUnit.SECONDS));
        else {
          pending.get(5, java.util.concurrent.TimeUnit.SECONDS);
          assertEquals(160, ids.size());
        }
        assertEquals(calls.get(), completed.get());
        org.junit.jupiter.api.Assertions.assertTrue(maximum.get() <= 4);
      } finally {
        releaseFirst.countDown();
        releaseOthers.countDown();
        caller.shutdownNow();
      }
    }
  }

}
