package org.metadatacenter.server.search.util;

/**
 * The coarse steps a search index rebuild passes through, in the order it passes through them.
 *
 * <p>A rebuild was reported as a single percentage or, until this existed, as nothing at all. Neither
 * says which of several long steps is under way, and that is the question an operator watching an
 * eight-hour job actually has. Two of these steps write no document and move no counter for minutes
 * at a time — reading every resource out of the graph, and counting the new index before promoting
 * it — so a rebuild that reports only "41% of 512,884" looks stalled during both of them.
 *
 * <p>Each phase carries its own counter rather than sharing one across the rebuild, because what is
 * being counted changes: pages of resources while enumerating, resources while indexing, nothing at
 * all while promoting. A percentage that silently changes denominator mid-run is worse than none.
 */
public enum IndexingPhase {

  /** Claimed, and on an executor, but not yet doing anything. */
  PENDING("waiting to start"),

  /**
   * Loading the caDSR value sets ontology, so CDE values can be indexed. Only CEDAR installations
   * that manage CDEs do this; elsewhere it fails fast with a warning and the rebuild carries on.
   */
  LOADING_VALUE_SETS("loading the value sets ontology"),

  /** Paging every resource out of the graph into the work list. No document is written yet. */
  ENUMERATING("reading every resource from the graph"),

  /**
   * Only when the rebuild was asked for without {@code force}: comparing the graph's resource ids
   * against the index's, to decide whether a rebuild is needed at all.
   */
  COMPARING("comparing the graph against the existing index"),

  /** The work itself: building each document and bulk-writing it to the new index. */
  INDEXING("building the new index"),

  /** Refreshing the new index and counting it, which must match the work list before it is promoted. */
  VERIFYING("counting the new index before promoting it"),

  /** Pointing the alias at the new index and deleting the indices it replaces. */
  PROMOTING("pointing the alias at the new index and removing the old ones"),

  /** The rebuild finished. Whether it succeeded is the job's state, not its phase. */
  DONE("finished");

  private final String description;

  IndexingPhase(String description) {
    this.description = description;
  }

  /** A phrase for a reader, so a status page need not keep its own table of these names. */
  public String getDescription() {
    return description;
  }
}
