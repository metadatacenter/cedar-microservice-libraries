package org.metadatacenter.server;

/** A revision-qualified Neo4j aggregate replacement did not match its current revision. */
public class RevisionConflictException extends RuntimeException {

  private final long currentRevision;

  public RevisionConflictException(long currentRevision) {
    super("Aggregate revision changed before update; current revision=" + currentRevision);
    this.currentRevision = currentRevision;
  }

  public long getCurrentRevision() {
    return currentRevision;
  }
}
