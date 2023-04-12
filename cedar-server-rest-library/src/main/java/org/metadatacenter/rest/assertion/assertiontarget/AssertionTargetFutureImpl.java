package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.error.CedarAssertionResult;

import javax.ws.rs.core.Response;
import java.util.Collection;

public abstract class AssertionTargetFutureImpl<T> implements AssertionTargetFuture {

  protected Collection<T> targets;
  protected CedarRequestContext requestContext;
  protected Collection<CedarAssertion> assertions;

  @Override
  public void otherwiseBadRequest() throws CedarAssertionException {
    buildAndThrowAssertionExceptionIfNeeded(getFirstAssertionError(), null, Response.Status.BAD_REQUEST);
  }

  @Override
  public void otherwiseBadRequest(CedarErrorPack errorPack) throws CedarAssertionException {
    buildAndThrowAssertionExceptionIfNeeded(getFirstAssertionError(), errorPack, Response.Status.BAD_REQUEST);
  }

  @Override
  public void otherwiseInternalServerError(CedarErrorPack errorPack) throws CedarAssertionException {
    buildAndThrowAssertionExceptionIfNeeded(getFirstAssertionError(), errorPack, Response.Status.INTERNAL_SERVER_ERROR);
  }

  @Override
  public void otherwiseNotFound(CedarErrorPack errorPack) throws CedarAssertionException {
    buildAndThrowAssertionExceptionIfNeeded(getFirstAssertionError(), errorPack, Response.Status.NOT_FOUND);
  }

  @Override
  public void otherwiseForbidden(CedarErrorPack errorPack) throws CedarAssertionException {
    buildAndThrowAssertionExceptionIfNeeded(getFirstAssertionError(), errorPack, Response.Status.FORBIDDEN);
  }

  protected CedarAssertionResult getFirstAssertionError() {
    CedarAssertionResult assertionError;
    for (T target : targets) {
      for (CedarAssertion assertion : assertions) {
        if (target instanceof CedarAssertionNoun) {
          assertionError = assertion.check(requestContext, (CedarAssertionNoun) target);
        } else {
          assertionError = assertion.check(requestContext, target);
        }
        if (assertionError != null) {
          return assertionError;
        }
      }
    }
    return null;
  }

  private void buildAndThrowAssertionExceptionIfNeeded(CedarAssertionResult assertionResult, CedarErrorPack errorPack,
                                                       Response.Status status) throws CedarAssertionException {
    if (assertionResult == null) {
      return;
    }
    if (errorPack != null) {
      errorPack.status(status);
      assertionResult.mergeErrorPack(errorPack);
      throw new CedarAssertionException(assertionResult, errorPack.getOperation());
    } else {
      assertionResult.status(status);
      throw new CedarAssertionException(assertionResult, null);
    }
  }


}
