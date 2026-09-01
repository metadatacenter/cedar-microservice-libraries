package org.metadatacenter.server.search.permission;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.queue.util.PermissionQueueService;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchPermissionEnqueueServiceTest {

  @Test
  void redisFailureLeavesTheEventDurableUntilALaterRelaySucceeds() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    InMemoryOutbox outbox = new InMemoryOutbox();
    when(queue.enqueueEvent(any())).thenReturn(false, true);
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);

    service.resourceMoved("resource-1");

    assertEquals(1, outbox.count(), "Redis refusal must not remove the durable event");

    service.relayPending();

    assertEquals(0, outbox.count(), "a later successful relay should acknowledge the event");
    verify(queue, org.mockito.Mockito.times(2)).enqueueEvent(any());
  }

  @Test
  void aNewServiceInstanceRelaysEventsLeftByThePreviousProcess() {
    InMemoryOutbox outbox = new InMemoryOutbox();
    PermissionQueueService unavailableQueue = mock(PermissionQueueService.class);
    when(unavailableQueue.enqueueEvent(any())).thenReturn(false);

    new SearchPermissionEnqueueService(unavailableQueue, outbox).groupDeleted("group-1");
    assertEquals(1, outbox.count());

    PermissionQueueService recoveredQueue = mock(PermissionQueueService.class);
    when(recoveredQueue.enqueueEvent(any())).thenReturn(true);
    SearchPermissionEnqueueService restarted = new SearchPermissionEnqueueService(recoveredQueue, outbox);
    restarted.relayPending();

    assertEquals(0, outbox.count(), "restart must not strand a pre-existing outbox event");
    verify(recoveredQueue).enqueueEvent(any());
  }

  @Test
  void aBacklogReadFailureDoesNotFailTheMutationThatAppendedANewDurableEvent() {
    PermissionQueueService queue = mock(PermissionQueueService.class);
    SearchPermissionOutbox outbox = mock(SearchPermissionOutbox.class);
    when(outbox.append(any())).thenReturn("new-event");
    when(outbox.pending(anyInt())).thenThrow(new IllegalArgumentException("malformed older event"));
    SearchPermissionEnqueueService service = new SearchPermissionEnqueueService(queue, outbox);

    assertDoesNotThrow(() -> service.resourceMoved("resource-1"));

    verify(outbox).append(any());
    verify(outbox, never()).remove(any());
    verify(queue, never()).enqueueEvent(any());
  }

  private static final class InMemoryOutbox implements SearchPermissionOutbox {
    private final Map<String, SearchPermissionQueueEvent> entries = new LinkedHashMap<>();

    @Override
    public String append(SearchPermissionQueueEvent event) {
      String id = UUID.randomUUID().toString();
      entries.put(id, event);
      return id;
    }

    @Override
    public List<Entry> pending(int limit) {
      List<Entry> pending = new ArrayList<>();
      entries.entrySet().stream().limit(limit)
          .forEach(entry -> pending.add(new Entry(entry.getKey(), entry.getValue())));
      return pending;
    }

    @Override
    public void remove(String outboxId) {
      entries.remove(outboxId);
    }

    @Override
    public long count() {
      return entries.size();
    }

    @Override
    public void close() {
    }
  }
}
