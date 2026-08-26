package org.metadatacenter.server.dao;

/**
 * The artifact changed after the caller read it, so a revision-qualified replacement matched nothing.
 */
public class ArtifactRevisionConflictException extends RuntimeException {

  public ArtifactRevisionConflictException(String id, long expectedRevision) {
    super("Artifact revision changed before update: id=" + id + ", expectedRevision=" + expectedRevision);
  }
}
