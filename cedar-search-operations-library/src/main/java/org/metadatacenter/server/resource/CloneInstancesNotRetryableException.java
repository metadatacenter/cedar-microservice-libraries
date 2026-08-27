package org.metadatacenter.server.resource;

import org.metadatacenter.exception.CedarProcessingException;

/**
 * A clone-instances failure that must not be retried.
 *
 * Cloning is not idempotent: it creates target folders and instance copies as it goes, so once a
 * run has changed the workspace, re-running the event duplicates every copy that succeeded. The
 * end-of-run per-instance failure report is equally unretryable, for a different reason: it names
 * instances no retry can fix — an ownerless legacy instance, an instance the new template rejects.
 * The queue processor dead-letters an event failing this way instead of re-running it.
 */
public class CloneInstancesNotRetryableException extends CedarProcessingException {

  public CloneInstancesNotRetryableException(String message) {
    super(message);
  }

  public CloneInstancesNotRetryableException(String message, Exception sourceException) {
    super(message, sourceException);
  }
}
