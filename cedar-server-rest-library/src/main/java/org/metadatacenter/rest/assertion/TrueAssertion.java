package org.metadatacenter.rest.assertion;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;

public class TrueAssertion implements CedarAssertion {

  TrueAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    return new CedarAssertionResult("Not implemented for ICedarAssertionNoun");
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    if (target != null) {
      if (target instanceof Boolean) {
        Boolean b = (Boolean) target;
        if (b.equals(Boolean.TRUE)) {
          return null;
        }
      }
    }
    return new CedarAssertionResult("The object should be true");
  }
}
