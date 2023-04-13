package org.metadatacenter.rest.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.assertion.noun.CedarParameterImpl;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.json.JsonMapper;

public class HttpRequestEmptyBody implements CedarRequestBody {

  public HttpRequestEmptyBody() {
  }

  @Override
  public CedarParameter get(String name) {
    return new CedarParameterImpl(name, CedarParameterSource.EmptyBody);
  }

  @Override
  public JsonNode asJson() {
    return JsonMapper.MAPPER.createObjectNode();
  }

  @Override
  public String asJsonString() throws CedarProcessingException {
    try {
      return JsonMapper.MAPPER.writeValueAsString(JsonMapper.MAPPER.createObjectNode());
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public <T> T convert(Class<T> type) throws CedarException {
    if (type == HttpRequestEmptyBody.class) {
      return (T) (new HttpRequestEmptyBody());
    }
    throw new CedarAssertionException("An empty body can not be converted into " + type);
  }
}
