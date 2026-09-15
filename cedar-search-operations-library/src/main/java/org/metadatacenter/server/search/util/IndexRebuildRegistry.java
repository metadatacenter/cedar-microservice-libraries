package org.metadatacenter.server.search.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The index a rebuild is currently filling, so that live writes can reach it too.
 *
 * <p><b>What this exists to fix.</b> Searches and live writes both go through the alias. A rebuild
 * builds a new index under its own name and leaves the alias on the old one, so search keeps working
 * while it runs — but every save, edit and delete during those hours also goes to the old index, and
 * promotion deletes the old index. An eight-hour rebuild therefore discarded eight hours of writes,
 * and the document-count check could not see it: the count is taken against the rebuild's own
 * enumeration snapshot, so it matched either way.
 *
 * <p><b>Two halves.</b> {@link #inProgressIndex()} tells the live indexing path where to mirror its
 * writes. {@link #wasWrittenLive} tells the rebuild which resources it must <em>not</em> write,
 * because a live write has already put a newer version there: the rebuild carries a snapshot taken
 * before the edit, so without this the rebuild would overwrite the fresh document with a stale one
 * whenever it reached that resource after the user's save. Documents are keyed by CEDAR id, so that
 * is a plain last-write-wins race, and skipping is what settles it in the newer write's favour.
 *
 * <p><b>Deletions</b> are recorded separately, so the count verified before promotion can describe
 * what should actually be in the new index rather than what the work list said.
 *
 * <p>This is a static facade over an {@link IndexRebuildStore}, because the live write path reaches
 * it from a service it does not construct. Which store is installed decides how far the state
 * reaches: in memory it is one process, which is wrong wherever the worker also indexes, and in Redis
 * it is every process that indexes. A deployment installs the Redis store at start-up; leaving the
 * default in place costs mirroring of whatever the other process writes, never correctness of a save.
 */
public final class IndexRebuildRegistry {

  private static final Logger log = LoggerFactory.getLogger(IndexRebuildRegistry.class);

  private static volatile IndexRebuildStore store = new InMemoryIndexRebuildStore();

  private IndexRebuildRegistry() {
  }

  /**
   * Put the rebuild's state where every indexing process can see it. Called once, at start-up, by
   * each server that indexes.
   */
  public static void install(IndexRebuildStore replacement) {
    store = replacement;
    log.info("Index rebuild state is held by {}", replacement.getClass().getSimpleName());
  }

  /** The store in use, for a caller that needs it directly rather than through this facade. */
  public static IndexRebuildStore store() {
    return store;
  }

  /** Restore the default, so one test cannot leave its store installed for the next. */
  static void reset() {
    store = new InMemoryIndexRebuildStore();
  }

  /** The rebuild announces the index it is filling, once it has created it. */
  public static void begin(String indexName) {
    store.begin(indexName);
    log.info("Live writes will be mirrored into the index being rebuilt: {}", indexName);
  }

  /**
   * The rebuild is over, however it ended. A name that is not the current one is ignored, so a job
   * whose index is no longer the one being built cannot switch off the mirroring a newer rebuild
   * depends on.
   */
  public static void end(String indexName) {
    store.end(indexName);
  }

  /** Where live writes should also go, or empty when no rebuild is running. */
  public static Optional<String> inProgressIndex() {
    return store.inProgressIndex();
  }

  /**
   * Note that a live write has placed a current version of this resource in the index being rebuilt,
   * so the rebuild must leave it alone.
   */
  public static void recordLiveWrite(String cedarId) {
    store.recordWrite(cedarId);
  }

  /** The same for a removal: the rebuild must neither write it back nor count it. */
  public static void recordLiveDelete(String cedarId) {
    store.recordDelete(cedarId);
  }

  /** Whether a live write has already put a newer document in the index being rebuilt. */
  public static boolean wasWrittenLive(String cedarId) {
    return cedarId != null && liveTouched().contains(cedarId);
  }

  /**
   * Everything a live write has touched since the rebuild began, as one set.
   *
   * <p>A rebuild reads this periodically and consults its own copy, rather than asking per resource:
   * against a Redis-backed store, a question per resource would be a round trip per resource, several
   * hundred thousand of them, for an answer that is almost always no. Reading it every batch instead
   * leaves a window in which a resource written live is not yet known to the rebuild and may be
   * overwritten with the version the rebuild started with — the race that existed before any of this,
   * now narrowed to one batch.
   */
  public static Set<String> liveTouched() {
    Set<String> touched = new HashSet<>(store.written());
    touched.addAll(store.deleted());
    return touched;
  }

  /**
   * How far the new index legitimately differs from the rebuild's work list, so the count verified
   * before promotion describes what should actually be there.
   *
   * <p>Mirroring makes the two diverge on purpose. A resource created during the rebuild is not in the
   * work list but is in the new index, so it adds one. A resource deleted during the rebuild is in the
   * work list but must not be in the new index, so it takes one away. A resource merely edited is in
   * both and changes nothing. A resource created and then deleted during the rebuild is in neither and
   * also changes nothing, which falls out of the two sets being kept disjoint.
   *
   * <p>Without this, the first deletion during a rebuild would fail the count check and refuse a
   * correct index after hours of work.
   */
  public static long expectedCountAdjustment(Set<String> snapshotIds) {
    if (store.overflowed()) {
      log.error("The count verified before promotion is approximate: too many resources were written"
          + " while the index was rebuilding and the rest were not tracked. If promotion is refused on"
          + " a document count, that is why, and the rebuild should simply be re-run.");
    }
    long created = store.written().stream().filter(id -> !snapshotIds.contains(id)).count();
    long deleted = store.deleted().stream().filter(snapshotIds::contains).count();
    return created - deleted;
  }
}
