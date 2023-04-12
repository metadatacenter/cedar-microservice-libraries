package org.metadatacenter.rest.assertion;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;

public interface CedarAssertion {

  CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target);

  CedarAssertionResult check(CedarRequestContext requestContext, Object target);
}
