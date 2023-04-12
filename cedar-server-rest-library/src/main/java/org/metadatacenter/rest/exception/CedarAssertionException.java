package org.metadatacenter.rest.exception;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.operation.CedarOperationDescriptor;

public class CedarAssertionException extends CedarException {

  public CedarAssertionException(String message) {
    super(message);
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

  public CedarAssertionException(Exception sourceException) {
    super(sourceException);
  }

}