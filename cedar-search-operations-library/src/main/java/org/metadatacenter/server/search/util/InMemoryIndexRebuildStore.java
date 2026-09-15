package org.metadatacenter.server.search.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The store one process keeps to itself.
 *
 * <p>This is the default, and it is the right one for a single-process test and for a deployment
 * where nothing but the resource server writes to the index. It is the wrong one wherever the worker
 * also indexes, because the two processes then disagree about whether a rebuild is running — which is
 * what {@link RedisIndexRebuildStore} exists to fix.
 */
public final class InMemoryIndexRebuildStore implements IndexRebuildStore {

  private static final Logger log = LoggerFactory.getLogger(InMemoryIndexRebuildStore.class);

  /**
   * How many identifiers are remembered before tracking stops.
   *
   * <p>Only what is written while a rebuild runs is held, normally a small fraction of the
   * repository. The cap is here so that an unusually busy rebuild degrades — back to the
   * last-write-wins race that was the behaviour before any of this — rather than growing without
   * limit on a server already doing the heaviest thing it ever does.
   */
  static final int MAX_TRACKED = 200_000;

  private volatile String inProgressIndex;
  private volatile String jobRecord;
  private volatile boolean overflowed;
  private final Set<String> written = ConcurrentHashMap.newKeySet();
  private final Set<String> deleted = ConcurrentHashMap.newKeySet();

  @Override
  public Optional<String> inProgressIndex() {
    return Optional.ofNullable(inProgressIndex);
  }

  @Override
  public synchronized void begin(String indexName) {
    written.clear();
    deleted.clear();
    overflowed = false;
    inProgressIndex = indexName;
  }

  @Override
  public synchronized void end(String indexName) {
    if (indexName == null || !indexName.equals(inProgressIndex)) {
      return;
    }
    inProgressIndex = null;
    written.clear();
    deleted.clear();
    overflowed = false;
  }

  @Override
  public void recordWrite(String cedarId) {
    if (track(written, cedarId)) {
      deleted.remove(cedarId);
    }
  }

  @Override
  public void recordDelete(String cedarId) {
    if (track(deleted, cedarId)) {
      written.remove(cedarId);
    }
  }

  private boolean track(Set<String> into, String cedarId) {
    if (cedarId == null || inProgressIndex == null) {
      return false;
    }
    if (written.size() + deleted.size() >= MAX_TRACKED) {
      if (!overflowed) {
        overflowed = true;
        log.warn("More than {} resources have been written while the index was rebuilding; the rebuild"
            + " will no longer skip them, so a resource edited during it may be indexed as it was at"
            + " the start of the rebuild", MAX_TRACKED);
      }
      return false;
    }
    into.add(cedarId);
    return true;
  }

  @Override
  public Set<String> written() {
    return Set.copyOf(written);
  }

  @Override
  public Set<String> deleted() {
    return Set.copyOf(deleted);
  }

  @Override
  public boolean overflowed() {
    return overflowed;
  }

  @Override
  public void saveJobRecord(String json) {
    jobRecord = json;
  }

  @Override
  public Optional<String> loadJobRecord() {
    return Optional.ofNullable(jobRecord);
  }

  @Override
  public void clearJobRecord() {
    jobRecord = null;
  }
}
