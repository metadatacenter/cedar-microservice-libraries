package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.queue.util.PermissionQueueService;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchPermissionEnqueueServiceTest {

  @Test
  void mutationsPersistWithoutReadingTheBacklogOrCallingRedis() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    SearchPermissionOutbox outbox = mock(SearchPermissionOutbox.class);
    when(outbox.append(any())).thenReturn("new-event");
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);

    service.resourceMoved("resource-1");

    verify(outbox).append(any());
    verifyNoMoreInteractions(outbox);
    verifyNoInteractions(queue);
  }

  @Test
  void redisFailureLeavesTheEventDurableUntilALaterRelaySucceeds() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = new InMemoryOutbox();
    when(queue.enqueueEvent(any())).thenReturn(false, true);
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);
    service.resourceMoved("resource-1");

    assertEquals(SearchPermissionEnqueueService.RelayResult.RETRY, service.relayPending());
    assertEquals(1, outbox.count());
    service.relayPending();

    assertEquals(0, outbox.count());
    verify(queue, times(2)).enqueueEvent(any());
  }

  @Test
  void partialDeliveryAcknowledgesOnlyTheSuccessfulPrefixInOneBatch() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = spy(new InMemoryOutbox());
    when(queue.enqueueEvent(any())).thenReturn(true, true, false);
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);
    for (int i = 0; i < 4; i++) service.resourceMoved("resource-" + i);
    List<String> expected = outbox.pending(2).stream().map(SearchPermissionOutbox.Entry::outboxId).toList();

    assertEquals(SearchPermissionEnqueueService.RelayResult.RETRY, service.relayPending());

    verify(outbox).remove(expected);
    assertEquals(List.of("resource-2", "resource-3"),
        outbox.pending(100).stream().map(entry -> entry.event().getId()).toList());
  }

  @Test
  void acknowledgementFailureRetainsEventsForSafeRedelivery() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = spy(new InMemoryOutbox());
    when(queue.enqueueEvent(any())).thenReturn(true);
    doThrow(new IllegalStateException("Neo4j unavailable")).doCallRealMethod().when(outbox).remove(anyList());
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);
    service.resourceMoved("resource-1");

    assertThrows(IllegalStateException.class, service::relayPending);
    assertEquals(1, outbox.count());
    service.relayPending();
    assertEquals(0, outbox.count());
    verify(queue, times(2)).enqueueEvent(any());
  }

  @Test
  void managedRelayDrainsMoreThanOneBatchOnRestartWithoutWaitingFiveSeconds() throws Exception {
    InMemoryOutbox outbox = spy(new InMemoryOutbox());
    SearchPermissionEnqueueService previous = new SearchPermissionEnqueueService(mock(PermissionQueueService.class), outbox);
    for (int i = 0; i < 250; i++) previous.groupDeleted("group-" + i);
    PermissionQueueService queue = mock(PermissionQueueService.class);
    when(queue.enqueueEvent(any())).thenReturn(true);
    CountDownLatch drained = new CountDownLatch(1);
    doAnswer(invocation -> {
      invocation.callRealMethod();
      if (outbox.count() == 0) drained.countDown();
      return null;
    }).when(outbox).remove(anyList());

    try (SearchPermissionEnqueueService restarted = new SearchPermissionEnqueueService(queue, outbox)) {
      restarted.start();
      restarted.start();
      assertTrue(drained.await(3, TimeUnit.SECONDS), "a backlog must drain without a five-second pause per batch");
      verify(outbox, times(3)).remove(anyList());
      verify(queue, times(250)).enqueueEvent(any());
    }
  }

  @Test
  void blockedRelayDoesNotBlockNewMutationsAndShutdownInterruptsIt() throws Exception {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = new InMemoryOutbox();
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch interrupted = new CountDownLatch(1);
    when(queue.enqueueEvent(any())).thenAnswer(invocation -> {
      entered.countDown();
      try {
        new CountDownLatch(1).await();
      } catch (InterruptedException e) {
        interrupted.countDown();
        Thread.currentThread().interrupt();
      }
      return false;
    });
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);
    try {
      service.resourceMoved("resource-1");
      service.start();
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      assertTimeoutPreemptively(Duration.ofSeconds(1), () -> service.resourceMoved("resource-2"));
      assertEquals(2, outbox.count());
      assertTimeoutPreemptively(Duration.ofSeconds(2), service::close);
      assertEquals(0, interrupted.getCount());
      assertEquals(2, outbox.count(), "shutdown must preserve undelivered events");
      service.close();
      verify(queue).close();
      assertThrows(IllegalStateException.class, service::start);
    } finally {
      service.close();
    }
  }

  @Test
  void idleRelayWakesPromptlyForAnAppend() throws Exception {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = spy(new InMemoryOutbox());
    CountDownLatch readEmpty = new CountDownLatch(1);
    CountDownLatch delivered = new CountDownLatch(1);
    doAnswer(invocation -> {
      Object entries = invocation.callRealMethod();
      readEmpty.countDown();
      return entries;
    }).when(outbox).pending(anyInt());
    when(queue.enqueueEvent(any())).thenAnswer(invocation -> { delivered.countDown(); return true; });
    try (SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox)) {
      service.start();
      assertTrue(readEmpty.await(3, TimeUnit.SECONDS));
      service.resourceMoved("resource-1");
      assertTrue(delivered.await(2, TimeUnit.SECONDS), "append should wake the idle relay");
    }
  }

  private static class InMemoryOutbox implements SearchPermissionOutbox {
    private final Map<String, SearchPermissionQueueEvent> entries = new LinkedHashMap<>();

    @Override
    public synchronized String append(SearchPermissionQueueEvent event) {
      String id = UUID.randomUUID().toString();
      entries.put(id, event);
      return id;
    }

    @Override
    public synchronized List<Entry> pending(int limit) {
      List<Entry> pending = new ArrayList<>();
      entries.entrySet().stream().limit(limit)
          .forEach(entry -> pending.add(new Entry(entry.getKey(), entry.getValue())));
      return pending;
    }

    @Override
    public synchronized void remove(List<String> outboxIds) {
      outboxIds.forEach(entries::remove);
    }

    @Override
    public void quarantineMalformed() { }

    @Override
    public synchronized long count() { return entries.size(); }

    @Override
    public void close() { }
  }
}
