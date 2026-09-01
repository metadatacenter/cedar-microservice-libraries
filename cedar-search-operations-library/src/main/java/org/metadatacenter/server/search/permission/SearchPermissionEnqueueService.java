package org.metadatacenter.server.search.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.server.queue.util.PermissionQueueService;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.metadatacenter.server.search.SearchPermissionQueueEventType.*;

public class SearchPermissionEnqueueService implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SearchPermissionEnqueueService.class);
  private static final int RELAY_BATCH_SIZE = 100;
  private static final int RELAY_INTERVAL_SECONDS = 5;

  private final PermissionQueueService queueService;
  private final SearchPermissionOutbox outbox;
  private ScheduledExecutorService relayExecutor;

  public SearchPermissionEnqueueService(CedarConfig cedarConfig) {
    this(new PermissionQueueService(cedarConfig.getCacheConfig().getPersistent()),
        new Neo4jSearchPermissionOutbox(cedarConfig));
  }

  SearchPermissionEnqueueService(PermissionQueueService queueService, SearchPermissionOutbox outbox) {
    this.queueService = queueService;
    this.outbox = outbox;
  }

  private void enqueue(String id, SearchPermissionQueueEventType eventType) {
    SearchPermissionQueueEvent event = new SearchPermissionQueueEvent(id, eventType);
    outbox.append(event);
    // The mutation has already committed and the event is now durable. A pre-existing malformed
    // entry or a transient queue failure must not turn that successful REST operation into a 500;
    // the managed relay will retry anything that remains in the outbox.
    relaySafely();
  }

  public synchronized void start() {
    if (relayExecutor != null) {
      return;
    }
    relayExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "search-permission-outbox-relay");
      thread.setDaemon(true);
      return thread;
    });
    relayExecutor.scheduleWithFixedDelay(this::relaySafely, 0, RELAY_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  synchronized void relayPending() {
    for (SearchPermissionOutbox.Entry entry : outbox.pending(RELAY_BATCH_SIZE)) {
      if (!queueService.enqueueEvent(entry.event())) {
        return;
      }
      outbox.remove(entry.outboxId());
    }
  }

  private void relaySafely() {
    try {
      relayPending();
    } catch (RuntimeException e) {
      log.error("The durable search-permission outbox could not be relayed; it will be retried", e);
    }
  }

  public long getPendingEventCount() {
    return outbox.count();
  }

  public void resourceMoved(String id) {
    enqueue(id, RESOURCE_MOVED);
  }

  public void resourcePermissionsChanged(CedarFilesystemResourceId id) {
    // TODO: Check if this was a real change. Check this at the calling side
    enqueue(id.getId(), RESOURCE_PERMISSION_CHANGED);
  }

  public void folderMoved(String id) {
    enqueue(id, FOLDER_MOVED);
  }

  public void folderPermissionsChanged(CedarFilesystemResourceId id) {
    // TODO: Check if this was a real change. Check this at the calling side
    enqueue(id.getId(), FOLDER_PERMISSION_CHANGED);
  }

  public void groupMembersUpdated(String id) {
    enqueue(id, GROUP_MEMBERS_UPDATED);
  }

  public void groupDeleted(String id) {
    enqueue(id, GROUP_DELETED);
  }

  @Override
  public synchronized void close() {
    if (relayExecutor != null) {
      relayExecutor.shutdownNow();
      relayExecutor = null;
    }
    queueService.close();
    outbox.close();
  }
}
