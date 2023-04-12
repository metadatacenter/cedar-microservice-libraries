package org.metadatacenter.rest.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.rest.context.HttpRequestEmptyBody;

public class NonEmptyAssertion implements CedarAssertion {

  NonEmptyAssertion() {
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, CedarAssertionNoun target) {
    if (!(target instanceof CedarParameter) && !(target instanceof CedarRequestBody)) {
      return new CedarAssertionResult(
          "Only instances of CedarParameter and CedarRequestBody can be checked with this assertion");
    }
    if (target instanceof CedarParameter) {
      CedarParameter cedarParameter = (CedarParameter) target;
      String s = cedarParameter.stringValue();
      if (s != null && !s.trim().isEmpty()) {
        return null;
      } else {
        return new CedarAssertionResult("You need to provide a non-null value for the parameter:"
            + cedarParameter.getName() + " from " + cedarParameter.getSource())
            .parameter("name", cedarParameter.getName())
            .parameter("source", cedarParameter.getSource())
            .badRequest();
      }
    } else {
      if (target instanceof HttpRequestEmptyBody) {
        return new CedarAssertionResult("You need to provide a non-empty request body").badRequest();
      }
      CedarRequestBody cedarRequestBody = (CedarRequestBody) target;
      JsonNode jsonNode = cedarRequestBody.asJson();
      if (jsonNode.isNull() || jsonNode.isMissingNode() || !jsonNode.isObject()) {
        return new CedarAssertionResult("You need to provide a non-null request body").badRequest();
      } else {
        return null;
      }
    }
  }

  @Override
  public CedarAssertionResult check(CedarRequestContext requestContext, Object target) {
    return new CedarAssertionResult("Not implemented for Objects");
  }

}
