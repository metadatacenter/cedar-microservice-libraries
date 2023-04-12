package org.metadatacenter.rest.assertion;

import org.apache.commons.validator.routines.UrlValidator;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.context.CedarRequestContext;

public class ValidUrlAssertion implements CedarAssertion {

  private static final UrlValidator urlValidator;

  static {
    String[] schemes = {"http", "https"};
    urlValidator = new UrlValidator(schemes);
  }

  ValidUrlAssertion() {
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
      String id = (String) target;
      if (id != null) {
        // There is no way currently to customize the URL Validator.
        // We replace our orgx TLD with the valid org just for this test
        id = id.replace(".orgx/", ".org/");
      }
      if (!urlValidator.isValid(id)) {
        return new CedarAssertionResult("The id is invalid:" + target).badRequest();
      }
      return null;
    }
  }

}
