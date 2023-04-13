package org.metadatacenter.rest.assertion;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.exception.CedarHelperException;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarUserNoun;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

public class PermissionChecker {
  public static CedarAssertionResult check(CedarAssertionNoun target,
                                           CedarPermission permission) {
    if (!(target instanceof CedarUserNoun)) {
      return new CedarAssertionResult("Only instances of CedarUserNoun can have permissions");
    }
    CedarUserNoun cedarUserNoun = (CedarUserNoun) target;
    //noinspection ConstantConditions
    CedarUser user = cedarUserNoun.getUser();
    if (user != null && permission != null) {
      if (user.getPermissions() == null || !user.getPermissions().contains(permission.getPermissionName())) {
        return new CedarAssertionResult("The user must have " + permission.getPermissionName() + "permission")
            .forbidden();
      } else {
        return null;
      }
    }
    CedarAssertionResult car = new CedarAssertionResult("Cedar User or permission is null")
        .internalServerError();
    car.getErrorPack().sourceException(new CedarHelperException());
    return car;
  }
}
