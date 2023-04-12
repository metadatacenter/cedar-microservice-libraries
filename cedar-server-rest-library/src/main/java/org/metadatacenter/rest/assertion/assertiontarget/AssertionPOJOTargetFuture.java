package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;

public interface AssertionPOJOTargetFuture extends AssertionTargetFuture {

  AssertionPOJOTargetFuture be(CedarAssertion... assertions);

}
