package org.metadatacenter.server;

/** A lifecycle precondition stopped holding before its graph transition could commit. */
public final class VersionTransitionConflictException extends IllegalStateException {
  public VersionTransitionConflictException(String message) { super(message); }
}
