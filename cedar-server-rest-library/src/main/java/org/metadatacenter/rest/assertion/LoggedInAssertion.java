package org.metadatacenter.rest.assertion;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.exception.CedarHelperException;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarUserNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

public class LoggedInAssertion implements CedarAssertion {

  LoggedInAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarUserNoun cedarUserNoun)) {
      return new CedarAssertionResult("Only instances of CedarUserNoun can be checked with this assertion");
    }
    CedarUser user = cedarUserNoun.user();
    if (user != null) {
      String cn = CedarPermission.LOGGED_IN.getPermissionName();
      if (user.getPermissions() == null || !user.getPermissions().contains(cn)) {
        return new CedarAssertionResult("The user must be logged in").forbidden();
      } else {
        return null;
      }
    }
    CedarAssertionResult car = new CedarAssertionResult(
        "You need to provide valid authorization data to execute REST calls")
        .unauthorized();
    car.getErrorPack().sourceException(new CedarHelperException());
    return car;
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }

}
