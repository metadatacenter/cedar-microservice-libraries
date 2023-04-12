package org.metadatacenter.rest.assertion;

import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarRequestNoun;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;

import static org.metadatacenter.constant.HttpConstants.CONTENT_TYPE_APPLICATION_MERGE_PATCH_JSON;

public class JsonMergePatchAssertion implements CedarAssertion {

  JsonMergePatchAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarRequestNoun)) {
      return new CedarAssertionResult("Only instances of CedarRequestNoun can be checked with this assertion");
    }
    String contentType;
    CedarRequestNoun cedarRequestNoun = (CedarRequestNoun) target;
    contentType = cedarRequestNoun.getContentType();
    if (CONTENT_TYPE_APPLICATION_MERGE_PATCH_JSON.equals(contentType)) {
      return null;
    }
    return new CedarAssertionResult("You need to provide a request with '" + CONTENT_TYPE_APPLICATION_MERGE_PATCH_JSON +
        "' convert content type!").badRequest();
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }
}
