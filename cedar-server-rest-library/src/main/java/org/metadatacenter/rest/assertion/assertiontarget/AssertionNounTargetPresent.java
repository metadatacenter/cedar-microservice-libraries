package org.metadatacenter.rest.assertion.assertiontarget;

import org.metadatacenter.rest.assertion.CedarAssertion;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.security.model.auth.CedarPermission;

public interface AssertionNounTargetPresent {

  void be(CedarAssertion... assertions) throws CedarAssertionException;

  void have(CedarPermission... permissions) throws CedarAssertionException;

}
