package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.rest.exception.CedarAssertionException;

public interface AssertionTargetFuture {

  void otherwiseBadRequest() throws CedarAssertionException;

  void otherwiseBadRequest(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseInternalServerError(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseNotFound(CedarErrorPack errorPack) throws CedarAssertionException;

  void otherwiseForbidden(CedarErrorPack errorPack) throws CedarAssertionException;
}
