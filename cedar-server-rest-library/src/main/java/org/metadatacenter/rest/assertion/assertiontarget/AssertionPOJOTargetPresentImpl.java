package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.error.CedarAssertionResult;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public class AssertionPOJOTargetPresentImpl implements AssertionPOJOTargetPresent {

  private final Collection<Object> targets;
  private final CedarRequestContext requestContext;

  public AssertionPOJOTargetPresentImpl(CedarRequestContext requestContext, Object... targets) {
    this.requestContext = requestContext;
    this.targets = new LinkedHashSet<>();
    Collections.addAll(this.targets, targets);
  }

  @Override
  public void be(CedarAssertion... assertions) throws CedarAssertionException {
    for (Object target : targets) {
      for (CedarAssertion assertion : assertions) {
        CedarAssertionResult result = assertion.check(requestContext, target);
        if (result != null) {
          throw new CedarAssertionException(result);
        }
      }
    }
  }
}
