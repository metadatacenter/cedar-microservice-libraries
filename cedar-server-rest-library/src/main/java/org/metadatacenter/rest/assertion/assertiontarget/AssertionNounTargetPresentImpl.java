package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.error.CedarSuggestedAction;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.assertion.PermissionChecker;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.security.model.auth.CedarPermission;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

public class AssertionNounTargetPresentImpl implements AssertionNounTargetPresent {

  private final Collection<CedarAssertionNoun> targets;
  private final CedarRequestContext requestContext;

  public AssertionNounTargetPresentImpl(CedarRequestContext requestContext, CedarAssertionNoun... targets) {
    this.requestContext = requestContext;
    this.targets = new LinkedHashSet<>();
    Collections.addAll(this.targets, targets);
  }

  @Override
  public void be(CedarAssertion... assertions) throws CedarAssertionException {
    for (CedarAssertionNoun target : targets) {
      for (CedarAssertion assertion : assertions) {
        CedarAssertionResult result = assertion.check(requestContext, target);
        if (result != null) {
          throw new CedarAssertionException(result);
        }
      }
    }
  }

  @Override
  public void have(CedarPermission... permissions) throws CedarAssertionException {
    for (CedarAssertionNoun target : targets) {
      for (CedarPermission permission : permissions) {
        CedarAssertionResult result = PermissionChecker.check(target, permission);
        if (result != null) {
          String permissionName = permission.getPermissionName();

          CedarErrorPack ep = new CedarErrorPack()
              .message("Missing permission: '" + permissionName + "'.")
              .errorKey(CedarErrorKey.PERMISSION_MISSING)
              .suggestedAction(CedarSuggestedAction.REQUEST_ROLE)
              .status(CedarResponseStatus.FORBIDDEN)
              .parameter("permissionName", permissionName);
          throw new CedarAssertionException(ep);
        }
      }
    }
  }
}
