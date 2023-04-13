package org.metadatacenter.rest.assertion.noun;

import org.metadatacenter.exception.CedarBadRequestException;
import org.metadatacenter.rest.CedarAssertionNoun;

import java.util.Optional;

public interface CedarRequest extends CedarAssertionNoun {

  CedarRequestBody getRequestBody() throws CedarBadRequestException;

  String getContentType();

  CedarParameter wrapQueryParam(String paramName, Optional<?> paramValue);

}
