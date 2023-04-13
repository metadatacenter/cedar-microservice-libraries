package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.util.Collections;
import java.util.LinkedHashSet;

public class AssertionPOJOTargetFutureImpl extends AssertionTargetFutureImpl<Object> implements AssertionPOJOTargetFuture {

  public AssertionPOJOTargetFutureImpl(CedarRequestContext requestContext, Object... targets) {
    this.requestContext = requestContext;
    this.targets = new LinkedHashSet<>();
    Collections.addAll(this.targets, targets);
    this.assertions = new LinkedHashSet<>();
  }

  @Override
  public AssertionPOJOTargetFutureImpl be(CedarAssertion... assertions) {
    Collections.addAll(this.assertions, assertions);
    return this;
  }
}
