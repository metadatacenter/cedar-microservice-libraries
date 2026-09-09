package org.metadatacenter.rest.exception;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.operation.CedarOperationDescriptor;

/**
 * A request that failed a check: a parameter the server refuses, a body it cannot read as what the
 * caller sent it for, two options that exclude each other.
 *
 * <p>An assertion is about the request, so the constructors that carry only a message or a cause
 * decide on 400 themselves. Before they did, the error pack fell through to its 500 default and every
 * site had to remember {@link #badRequest()}, and the sites that forgot reported a caller's typo as a
 * server fault. The constructors that take a {@link CedarAssertionResult} or a pack keep the status
 * that result decided, since the assertion pipeline chooses 401, 403 and 404 as well as 400. A failure
 * in the server's own work is a {@link org.metadatacenter.exception.CedarProcessingException}, not an
 * assertion.
 */
public class CedarAssertionException extends CedarException {

  public CedarAssertionException(String message) {
    super(message);
    errorPack.status(CedarResponseStatus.BAD_REQUEST);
  }

  public CedarAssertionException(String message, Exception sourceException) {
    super(message, sourceException);
    errorPack.status(CedarResponseStatus.BAD_REQUEST);
  }

  public CedarAssertionException(Exception sourceException) {
    super(sourceException);
    errorPack.status(CedarResponseStatus.BAD_REQUEST);
  }

  public CedarAssertionException(CedarAssertionResult result, CedarOperationDescriptor operation) {
    super(result.getErrorPack());
    if (operation != null) {
      this.errorPack.operation(operation);
    }
  }

  public CedarAssertionException(CedarErrorPack errorPack) {
    super(errorPack);
  }

  public CedarAssertionException(CedarAssertionResult result) {
    super(result.getErrorPack());
  }

}
