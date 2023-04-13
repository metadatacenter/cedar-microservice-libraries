package org.metadatacenter.rest.context;

import org.metadatacenter.rest.assertion.noun.CedarUserNoun;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;

public class LocalRequestContext extends AbstractRequestContext {

  LocalRequestContext(LinkedDataUtil linkedDataUtil, CedarUser cedarUser) {
    currentUser = cedarUser;
    wrappedRequest = new LocalRequest(currentUser);
    user = new CedarUserNoun(currentUser);
    this.linkedDataUtil = linkedDataUtil;
  }

  @Override
  public String getAuthorizationHeader() {
    return currentUser.getFirstApiKeyAuthHeader();
  }

  @Override
  public String getDebugHeader() {
    return "true";
  }

  @Override
  public String getGlobalRequestIdHeader() {
    return null;
  }

  @Override
  public String getLocalRequestIdHeader() {
    return null;
  }

  @Override
  public String getClientSessionIdHeader() {
    return null;
  }

  @Override
  public String getSourceHashHeader() {
    return null;
  }

}
