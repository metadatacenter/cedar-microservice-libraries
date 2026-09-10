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

  /**
   * Refuses a body carrying a property this endpoint does not accept, and returns the body so a
   * caller can read its properties straight afterwards.
   *
   * <p>A command or options body is a closed contract. Reading only the properties the endpoint
   * knows would discard a misspelled one in silence, and the caller would see a request that
   * succeeded and changed nothing. A body that is not a JSON object carries no properties to
   * check, so the endpoint's own parameter checks answer for it.
   */
  CedarRequestBody mustHaveOnly(String... accepted) throws CedarException;

}
