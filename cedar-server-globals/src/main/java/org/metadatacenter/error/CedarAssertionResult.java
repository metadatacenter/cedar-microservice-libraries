package org.metadatacenter.error;

import org.metadatacenter.server.result.BackendCallError;
import org.metadatacenter.server.result.BackendCallResult;

import javax.ws.rs.core.Response;

public class CedarAssertionResult {

  private CedarErrorPack errorPack;

  public CedarAssertionResult(String message) {
    this.errorPack = new CedarErrorPack();
    errorPack.message(message);
  }

  public CedarAssertionResult(BackendCallResult backendCallResult) {
    this(backendCallResult.getFirstErrorMessage());
    BackendCallError firstError = backendCallResult.getFirstError();
    if (firstError != null) {
      errorPack = new CedarErrorPack(firstError.getErrorPack());
    }
  }

  public CedarAssertionResult internalServerError() {
    errorPack.status(Response.Status.INTERNAL_SERVER_ERROR);
    return this;
  }

  public CedarAssertionResult forbidden() {
    errorPack.status(Response.Status.FORBIDDEN);
    return this;
  }

  public CedarAssertionResult unauthorized() {
    errorPack.status(Response.Status.UNAUTHORIZED);
    return this;
  }

  public CedarAssertionResult notFound() {
    errorPack.status(Response.Status.NOT_FOUND);
    return this;
  }

  public CedarAssertionResult badRequest() {
    errorPack.status(Response.Status.BAD_REQUEST);
    return this;
  }

  public CedarAssertionResult parameter(String name, Object value) {
    this.errorPack.parameter(name, value);
    return this;
  }

  public CedarAssertionResult message(String message) {
    this.errorPack.message(message);
    return this;
  }

  public CedarAssertionResult status(Response.Status status) {
    this.errorPack.status(status);
    return this;
  }

  public CedarErrorPack getErrorPack() {
    return errorPack;
  }

  public void mergeErrorPack(CedarErrorPack errorPack) {
    this.errorPack.merge(errorPack);
  }
}