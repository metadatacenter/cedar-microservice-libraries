package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.server.security.model.auth.CedarPermission;

public interface AssertionNounTargetFuture extends AssertionTargetFuture {

  AssertionNounTargetFuture be(CedarAssertion... assertions);

  AssertionNounTargetFuture have(CedarPermission... permissions);
}
