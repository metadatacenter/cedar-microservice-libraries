package org.metadatacenter.server.dao;

/**
 * The store moved on between the caller's read and its write. Either a revision-qualified replacement
 * or deletion matched nothing, or an insert of an identifier the read found absent was rejected by the
 * unique index on {@code @id} because another writer created it first.
 *
 * <p>{@link TemplateReferencedException} is the one refusal of this type that is not a race: the template
 * store refuses to delete or restructure a template that instances reference.
 */
public class ArtifactRevisionConflictException extends RuntimeException {

  public ArtifactRevisionConflictException(String id, long expectedRevision) {
    super("Artifact revision changed before update: id=" + id + ", expectedRevision=" + expectedRevision);
  }

  public ArtifactRevisionConflictException(String id) {
    super("Artifact was created by another writer before insert: id=" + id);
  }

  /** For a refusal that says more precisely than these why the write could not be applied. */
  protected ArtifactRevisionConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
