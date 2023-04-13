package org.metadatacenter.rest.context;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.assertiontarget.AssertionNounTargetFuture;
import org.metadatacenter.rest.assertion.assertiontarget.AssertionNounTargetPresent;
import org.metadatacenter.rest.assertion.assertiontarget.AssertionPOJOTargetFuture;
import org.metadatacenter.rest.assertion.assertiontarget.AssertionPOJOTargetPresent;
import org.metadatacenter.rest.assertion.noun.CedarAssertionUser;
import org.metadatacenter.rest.assertion.noun.CedarRequest;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;

public interface CedarRequestContext {

  CedarRequest request();

  CedarAssertionUser user();

  AssertionNounTargetFuture should(CedarAssertionNoun... nouns);

  AssertionPOJOTargetFuture should(Object... objects);

  AssertionNounTargetPresent must(CedarAssertionNoun... nouns);

  AssertionPOJOTargetPresent must(Object... objects);

  CedarUser getCedarUser();

  CedarAccessException getUserCreationException();

  LinkedDataUtil getLinkedDataUtil();

  String getAuthorizationHeader();

  String getDebugHeader();

  String getGlobalRequestIdHeader();

  String getLocalRequestIdHeader();

  String getClientSessionIdHeader();

  String getSourceHashHeader();
}
