package org.metadatacenter.server;

import java.util.Set;

/** The parsed strong validators supplied in an If-Match request header. */
public record RevisionPrecondition(boolean anyCurrentRevision, Set<Long> revisions) {

  public RevisionPrecondition {
    revisions = revisions == null ? Set.of() : Set.copyOf(revisions);
  }

  public static RevisionPrecondition any() {
    return new RevisionPrecondition(true, Set.of());
  }

  public static RevisionPrecondition exact(long revision) {
    return new RevisionPrecondition(false, Set.of(revision));
  }

  public static RevisionPrecondition none() {
    return new RevisionPrecondition(false, Set.of());
  }

  public boolean matches(long currentRevision) {
    return anyCurrentRevision || revisions.contains(currentRevision);
  }
}
