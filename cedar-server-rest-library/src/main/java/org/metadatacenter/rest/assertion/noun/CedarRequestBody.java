package org.metadatacenter.rest.assertion.noun;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.CedarAssertionNoun;

public interface CedarRequestBody extends CedarAssertionNoun {

  CedarParameter get(String name);

  JsonNode asJson();

  String asJsonString() throws CedarProcessingException;

  <T> T convert(Class<T> type) throws CedarException;

}
