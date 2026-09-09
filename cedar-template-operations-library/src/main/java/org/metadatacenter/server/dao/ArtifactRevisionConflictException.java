package org.metadatacenter.server.dao;

/**
 * The store moved on between the caller's read and its write. Either a revision-qualified replacement
 * or deletion matched nothing, or an insert of an identifier the read found absent was rejected by the
 * unique index on {@code @id} because another writer created it first.
 */
public class ArtifactRevisionConflictException extends RuntimeException {

  public ArtifactRevisionConflictException(String id, long expectedRevision) {
    super("Artifact revision changed before update: id=" + id + ", expectedRevision=" + expectedRevision);
  }

  public ArtifactRevisionConflictException(String id) {
    super("Artifact was created by another writer before insert: id=" + id);
  }
}
