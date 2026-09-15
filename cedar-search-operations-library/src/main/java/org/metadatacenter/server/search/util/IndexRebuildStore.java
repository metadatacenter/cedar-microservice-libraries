package org.metadatacenter.server.search.util;

import java.util.Optional;
import java.util.Set;

/**
 * Where the state of an index rebuild lives, so that more than one process can see it.
 *
 * <p>The state started out as static fields, which was enough while only the resource server wrote to
 * the index. It is not: {@code NodeIndexingService} is also constructed in the worker, which applies
 * the permission cascade, and a worker holding its own copy of this state sees no rebuild running and
 * mirrors nothing. Every permission update it applied during a rebuild went only to the index the
 * alias named, and died when that index was deleted on promotion.
 *
 * <p>The same problem, one level up, is that a rebuild's own record does not survive the process:
 * the resource server restarting mid-rebuild left a status saying nothing had ever run. Both are the
 * same requirement — state that outlives one JVM — so both live here rather than growing a second
 * mechanism.
 *
 * <p>Implementations must be safe for concurrent use and must not throw: a store that cannot be
 * reached degrades to "no rebuild is running", which costs mirroring but never a user's save.
 */
public interface IndexRebuildStore {

  /** The index a rebuild is filling, or empty when none is. */
  Optional<String> inProgressIndex();

  /** Start mirroring into this index, discarding whatever a previous rebuild tracked. */
  void begin(String indexName);

  /**
   * Stop mirroring into this index. A name that is not the current one is ignored, so a failed
   * older rebuild reaching its cleanup cannot switch off the mirroring a newer one depends on.
   */
  void end(String indexName);

  void recordWrite(String cedarId);

  void recordDelete(String cedarId);

  /** Ids a live write has placed in the rebuilding index. */
  Set<String> written();

  /** Ids a live write has removed from it. */
  Set<String> deleted();

  /** Whether the store stopped tracking ids because too many arrived. */
  boolean overflowed();

  /**
   * Keep the rebuild's own status record, as JSON, so a poll after a restart can say what was
   * running rather than that nothing ever was.
   */
  void saveJobRecord(String json);

  /** The last status record kept, or empty if none was. */
  Optional<String> loadJobRecord();

  /** Forget the status record, once its job has been accounted for. */
  void clearJobRecord();
}
