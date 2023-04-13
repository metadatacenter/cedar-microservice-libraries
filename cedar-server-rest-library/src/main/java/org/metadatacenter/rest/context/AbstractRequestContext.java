package org.metadatacenter.rest.context;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.assertiontarget.*;
import org.metadatacenter.rest.assertion.noun.CedarAssertionUser;
import org.metadatacenter.rest.assertion.noun.CedarRequest;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;

public abstract class AbstractRequestContext implements CedarRequestContext {

  protected CedarUser currentUser;
  protected CedarAssertionUser user;
  protected CedarRequest wrappedRequest;
  protected CedarAccessException userCreationException;
  protected LinkedDataUtil linkedDataUtil;

  @Override
  public CedarAssertionUser user() {
    return user;
  }

  @Override
  public AssertionNounTargetFuture should(CedarAssertionNoun... targets) {
    return new AssertionNounTargetFutureImpl(this, targets);
  }

  @Override
  public AssertionPOJOTargetFuture should(Object... targets) {
    return new AssertionPOJOTargetFutureImpl(this, targets);
  }

  @Override
  public AssertionNounTargetPresent must(CedarAssertionNoun... targets) {
    return new AssertionNounTargetPresentImpl(this, targets);
  }

  @Override
  public AssertionPOJOTargetPresent must(Object... targets) {
    return new AssertionPOJOTargetPresentImpl(this, targets);
  }

  @Override
  public CedarUser getCedarUser() {
    return currentUser;
  }

  @Override
  public CedarRequest request() {
    return wrappedRequest;
  }

  @Override
  public CedarAccessException getUserCreationException() {
    return userCreationException;
  }

  @Override
  public LinkedDataUtil getLinkedDataUtil() {
    return linkedDataUtil;
  }

}
