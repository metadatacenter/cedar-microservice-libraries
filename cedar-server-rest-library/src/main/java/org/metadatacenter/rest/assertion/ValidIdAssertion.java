package org.metadatacenter.rest.assertion;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.jsonld.LinkedDataUtil;

public class ValidIdAssertion implements CedarAssertion {

  ValidIdAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    // we only check strings
    return null;
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    if (!(target instanceof String)) {
      return new CedarAssertionResult("The id should be a non-null String");
    } else {
      LinkedDataUtil linkedDataUtil = requestContext.getLinkedDataUtil();
      String id = (String) target;
      boolean isValid = linkedDataUtil.isValidId(id);
      if (!isValid) {
        return new CedarAssertionResult("The id is invalid:" + id).badRequest();
      }
      return null;
    }
  }

}
