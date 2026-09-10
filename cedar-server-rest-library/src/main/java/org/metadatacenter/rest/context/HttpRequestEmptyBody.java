package org.metadatacenter.rest.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.error.CedarErrorKey;
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
    return JsonMapper.STRICT_MAPPER.createObjectNode();
  }

  @Override
  public String asJsonString() throws CedarProcessingException {
    try {
      return JsonMapper.STRICT_MAPPER.writeValueAsString(JsonMapper.STRICT_MAPPER.createObjectNode());
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public CedarRequestBody mustHaveOnly(String... accepted) {
    return this;
  }

  @Override
  public <T> T convert(Class<T> type) throws CedarException {
    if (type == HttpRequestEmptyBody.class) {
      return (T) (new HttpRequestEmptyBody());
    }
    throw new CedarAssertionException("The request has no body to read as " + type.getSimpleName())
        .errorKey(CedarErrorKey.MISSING_DATA)
        .parameter("type", type.getSimpleName());
  }
}
