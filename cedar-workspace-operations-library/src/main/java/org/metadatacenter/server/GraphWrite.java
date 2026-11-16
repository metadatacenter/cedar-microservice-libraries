package org.metadatacenter.server;

import org.neo4j.driver.Transaction;

/**
 * A write a caller adds to the graph transaction of a change, so that both commit or neither does. A
 * permission change uses it to record the search-permission event its change requires: written in a
 * transaction of its own after the change committed, the event could fail and leave the index on the
 * old keys with nothing left to correct them.
 */
@FunctionalInterface
public interface GraphWrite {

  /** Adds nothing. */
  GraphWrite NONE = transaction -> {
  };

  /** Runs this write in the transaction of the change, after the change itself succeeded. */
  void writeIn(Transaction transaction);
}
