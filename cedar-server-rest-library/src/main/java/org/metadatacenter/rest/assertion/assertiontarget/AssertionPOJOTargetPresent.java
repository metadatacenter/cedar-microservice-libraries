package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.exception.CedarAssertionException;

public interface AssertionPOJOTargetPresent {

  void be(CedarAssertion... assertions) throws CedarAssertionException;

}
