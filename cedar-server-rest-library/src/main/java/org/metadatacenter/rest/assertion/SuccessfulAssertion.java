package org.metadatacenter.rest.assertion;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.server.result.BackendCallResult;

public class SuccessfulAssertion implements CedarAssertion {

  SuccessfulAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    return new CedarAssertionResult("Not implemented for ICedarAssertionNoun");
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    if (target != null) {
      if (target instanceof BackendCallResult backendCallResult) {
        if (backendCallResult.isError()) {
          return new CedarAssertionResult(backendCallResult);
        } else {
          return null;
        }
      }
    }
    return new CedarAssertionResult("A BackendCallResult should be tested!");
  }
}
