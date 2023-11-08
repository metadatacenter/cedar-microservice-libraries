package org.metadatacenter.rest.assertion;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;

public class NonNullAssertion implements CedarAssertion {

  NonNullAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (target == null) {
      return new CedarAssertionResult("The object should be not null");
    } else {
      if (target instanceof CedarParameter param) {
        if (param.isMissing()) {
          return new CedarAssertionResult("The parameter named '" + param.getName() + "' from " + param.getSource() +
              " should be present")
              .parameter("name", param.getName())
              .parameter("source", param.getSource());
        } else if (param.isNull()) {
          return new CedarAssertionResult("The parameter named '" + param.getName() + "' from " + param.getSource() +
              " should be not null")
              .parameter("name", param.getName())
              .parameter("source", param.getSource());
        } else {
          return null;
        }
      }
    }
    return null;
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    if (target != null) {
      return null;
    } else {
      return new CedarAssertionResult("The object should not be null");
    }
  }

}
