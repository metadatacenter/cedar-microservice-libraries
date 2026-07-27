package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.rest.exception.CedarAssertionException;

public interface AssertionTargetFuture {

  void otherwiseBadRequest() throws CedarAssertionException;

  void otherwiseBadRequest(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseInternalServerError(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseNotFound(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseForbidden(CedarErrorPack errorPack) throws CedarAssertionException;

  /**
   * Fails with 409 Conflict: the request is well formed and permitted, but collides with the
   * current state — a name already taken, a resource that already exists. Without this, such a
   * check has to report 400, which a client cannot tell apart from a malformed request.
   */
  void otherwiseConflict(CedarErrorPack errorPack) throws CedarAssertionException;
}
