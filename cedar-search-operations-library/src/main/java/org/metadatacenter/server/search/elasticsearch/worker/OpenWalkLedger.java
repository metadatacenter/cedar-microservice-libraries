package org.metadatacenter.server.search.elasticsearch.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The point in time contexts this process's search walks hold open, by when each walk last asked for
 * a page.
 *
 * <p>A walk keeps its context between requests and releases it when a page comes back short. A
 * client that stops reading before then leaves the context open until its keep alive runs out.
 * OpenSearch refuses to open a context past {@code search.max_open_pit_context}, 300 by default, and
 * that refusal fails every deep search, for every caller. On a small store most walks reach their
 * last page at once, so few contexts stay open. On a production-sized store most result sets outrun
 * the pages a client reads, so contexts stay open at the rate walks are started, and a few walks a
 * second fill the cluster within one keep alive.
 *
 * <p>The ledger bounds what this process holds. When a walk would take it past its capacity, the
 * walk that has gone longest without a page is returned for closing. Its next page finds its context
 * gone and is answered as an expired continuation, which tells the client to start again.
 *
 * <p>A context whose keep alive has run out is returned for closing as well. OpenSearch frees an
 * expired context only when its reaper next runs, once a minute by default, and counts it against the
 * limit until then. Closing it frees it at once.
 */
final class OpenWalkLedger {

  private final int capacity;
  private final Duration keepAlive;
  private final Supplier<Instant> clock;
  /** In order of last use, oldest first. */
  private final LinkedHashMap<String, Instant> lastUsed = new LinkedHashMap<>(16, 0.75f, true);

  OpenWalkLedger(int capacity, Duration keepAlive, Supplier<Instant> clock) {
    if (capacity < 1) {
      throw new IllegalArgumentException("A ledger must hold at least one walk; got " + capacity);
    }
    this.capacity = capacity;
    this.keepAlive = keepAlive;
    this.clock = clock;
  }

  /**
   * The contexts a walk's use of its own leaves to close, oldest first: those whose keep alive has run
   * out, and those closed to stay within the capacity.
   */
  record ToClose(List<String> expired, List<String> abandoned) {
  }

  /**
   * Record that a walk is using its context now, and answer the contexts to close. The context just
   * recorded is never among them.
   */
  synchronized ToClose use(String pointInTimeId) {
    Instant now = clock.get();
    lastUsed.put(pointInTimeId, now);
    List<String> expired = new ArrayList<>();
    Iterator<Map.Entry<String, Instant>> oldestFirst = lastUsed.entrySet().iterator();
    while (oldestFirst.hasNext()) {
      Map.Entry<String, Instant> entry = oldestFirst.next();
      if (entry.getValue().plus(keepAlive).isAfter(now)) {
        break;
      }
      expired.add(entry.getKey());
      oldestFirst.remove();
    }
    List<String> abandoned = new ArrayList<>();
    oldestFirst = lastUsed.entrySet().iterator();
    while (lastUsed.size() > capacity) {
      abandoned.add(oldestFirst.next().getKey());
      oldestFirst.remove();
    }
    return new ToClose(expired, abandoned);
  }

  /** Forget a context that has been closed, or that OpenSearch no longer holds. */
  synchronized void release(String pointInTimeId) {
    lastUsed.remove(pointInTimeId);
  }

  synchronized int size() {
    return lastUsed.size();
  }
}
