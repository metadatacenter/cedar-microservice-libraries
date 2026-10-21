package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.assertion.PermissionChecker;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;

@SuppressWarnings("ALL")
public class AssertionNounTargetFutureImpl extends AssertionTargetFutureImpl<CedarAssertionNoun> implements
    AssertionNounTargetFuture {

  private final Collection<CedarPermission> permissions;

  public AssertionNounTargetFutureImpl(CedarRequestContext requestContext, CedarAssertionNoun... targets) {
    this.requestContext = requestContext;
    this.targets = new LinkedHashSet<>();
    Collections.addAll(this.targets, targets);
    this.assertions = new LinkedHashSet<>();
    this.permissions = new LinkedHashSet<>();
  }

  @Override
  public AssertionNounTargetFutureImpl be(CedarAssertion... assertions) {
    Collections.addAll(this.assertions, assertions);
    return this;
  }

  @Override
  public AssertionNounTargetFutureImpl have(CedarPermission... permissions) {
    Collections.addAll(this.permissions, permissions);
    return this;
  }

  @Override
  protected CedarAssertionResult getFirstAssertionError() {
    CedarAssertionResult assertionError = super.getFirstAssertionError();
    if (assertionError != null) {
      return assertionError;
    }
    for (CedarAssertionNoun target : targets) {
      for (CedarPermission permission : permissions) {
        assertionError = PermissionChecker.check(target, permission);
        if (assertionError != null) {
          return assertionError;
        }
      }
    }
    return null;
  }

}
