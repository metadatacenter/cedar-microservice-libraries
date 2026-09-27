package org.metadatacenter.server.search.permission;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.server.queue.util.PermissionQueueService;
import org.metadatacenter.server.search.SearchPermissionQueueEvent;
import org.metadatacenter.server.search.SearchPermissionQueueEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.metadatacenter.server.search.SearchPermissionQueueEventType.*;

public class SearchPermissionEnqueueService implements AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(SearchPermissionEnqueueService.class);
  private static final int RELAY_BATCH_SIZE = 100;
  private static final int RELAY_INTERVAL_SECONDS = 5;

  private final PermissionQueueService queueService;
  private final SearchPermissionOutbox outbox;
  private final Object lifecycleLock = new Object();
  private final Semaphore wakeup = new Semaphore(0);
  private ExecutorService relayExecutor;
  private volatile boolean closed;

  enum RelayResult { IDLE, MORE, RETRY }

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
    // Only persistence belongs on the request thread. Coalesce notifications; the durable
    // outbox, not the wakeup, is the source of truth (including across process restarts).
    if (wakeup.availablePermits() == 0) {
      wakeup.release();
    }
  }

  public void start() {
    synchronized (lifecycleLock) {
      if (closed) {
        throw new IllegalStateException("The search-permission relay is closed");
      }
      if (relayExecutor != null) {
        return;
      }
      relayExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "search-permission-outbox-relay");
        thread.setDaemon(true);
        return thread;
      });
      relayExecutor.execute(this::runRelay);
    }
  }

  private void runRelay() {
    long nextQuarantine = 0;
    while (!closed && !Thread.currentThread().isInterrupted()) {
      wakeup.drainPermits();
      if (System.nanoTime() >= nextQuarantine) {
        try {
          outbox.quarantineMalformed();
        } catch (RuntimeException e) {
          log.error("Could not quarantine malformed search-permission events; maintenance will retry", e);
        }
        nextQuarantine = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
      }
      RelayResult result;
      try {
        result = relayPending();
      } catch (RuntimeException e) {
        log.error("The durable search-permission outbox could not be relayed; it will be retried", e);
        result = RelayResult.RETRY;
      }
      try {
        if (result == RelayResult.RETRY) {
          // New mutations must not turn a Redis outage into a busy retry loop.
          TimeUnit.SECONDS.sleep(RELAY_INTERVAL_SECONDS);
        } else if (result == RelayResult.IDLE) {
          wakeup.tryAcquire(RELAY_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
        // A full delivered batch continues immediately, rather than limiting throughput
        // to 100 events per five seconds. Check shutdown and maintenance between batches.
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  RelayResult relayPending() {
    List<SearchPermissionOutbox.Entry> entries = outbox.pending(RELAY_BATCH_SIZE);
    List<String> delivered = new ArrayList<>(entries.size());
    boolean retry = false;
    try {
      for (SearchPermissionOutbox.Entry entry : entries) {
        if (closed || Thread.currentThread().isInterrupted() || !queueService.enqueueEvent(entry.event())) {
          retry = true;
          break;
        }
        delivered.add(entry.outboxId());
      }
    } finally {
      // A partial batch acknowledges only Redis-confirmed events. If acknowledgement
      // fails, delivery may repeat, which is safe for the idempotent permission projection.
      if (!delivered.isEmpty()) {
        outbox.remove(delivered);
      }
    }
    return retry ? RelayResult.RETRY
        : entries.size() == RELAY_BATCH_SIZE ? RelayResult.MORE : RelayResult.IDLE;
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
  public void close() {
    ExecutorService executor;
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      executor = relayExecutor;
      if (executor != null) {
        executor.shutdownNow();
      }
    }
    // Never acquire a monitor held across relay I/O. Bound graceful shutdown, then close
    // the clients to release outstanding I/O; unacknowledged events remain durable.
    try {
      if (executor != null && !executor.awaitTermination(RELAY_INTERVAL_SECONDS, TimeUnit.SECONDS)) {
        log.warn("Closing search-permission relay clients after the shutdown grace period");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      try {
        queueService.close();
      } finally {
        outbox.close();
      }
    }
  }
}
