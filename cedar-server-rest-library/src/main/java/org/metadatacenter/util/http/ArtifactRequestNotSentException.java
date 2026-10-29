package org.metadatacenter.util.http;

import org.metadatacenter.exception.CedarDependencyUnavailableException;

/**
 * A request the artifact service never received. No connection to it opened, and the request is one
 * the client attempts only once, so no earlier attempt can have reached it either. The service cannot
 * have acted on the request, and a create sent this way stored nothing.
 */
public final class ArtifactRequestNotSentException extends CedarDependencyUnavailableException {

  ArtifactRequestNotSentException(Exception sourceException) {
    super("Downstream service is unavailable", sourceException);
  }
}
