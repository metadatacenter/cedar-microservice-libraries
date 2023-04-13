package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.rest.CedarAssertionNoun;

import java.util.Optional;

public abstract class CedarRequestNoun implements CedarRequest, CedarAssertionNoun {

  @Override
  public CedarParameter wrapQueryParam(String paramName, Optional<?> paramValue) {
    return new CedarWrappedQueryParameter(paramName, paramValue);
  }

}
