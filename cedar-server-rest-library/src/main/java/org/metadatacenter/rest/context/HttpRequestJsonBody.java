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

public class HttpRequestJsonBody implements CedarRequestBody {

  private final JsonNode bodyNode;

  public HttpRequestJsonBody() {
    bodyNode = null;
  }

  public HttpRequestJsonBody(JsonNode bodyNode) {
    this.bodyNode = bodyNode;
  }

  @Override
  public CedarParameter get(String name) {
    CedarParameterImpl p = new CedarParameterImpl(name, CedarParameterSource.JsonBody);
    if (bodyNode != null) {
      JsonNode jsonNode = bodyNode.get(name);
      if (jsonNode != null && !jsonNode.isMissingNode()) {
        p.setJsonNode(jsonNode);
      }
    }
    return p;
  }

  @Override
  public JsonNode asJson() {
    return bodyNode;
  }

  @Override
  public String asJsonString() throws CedarProcessingException {
    try {
      return JsonMapper.MAPPER.writeValueAsString(bodyNode);
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public <T> T convert(Class<T> type) throws CedarException {
    try {
      return JsonMapper.MAPPER.treeToValue(bodyNode, type);
    } catch (JsonProcessingException e) {
      // The body parsed as JSON, so this is a shape the endpoint does not accept: an unknown key, a
      // value of the wrong type. That is the caller's to fix, and Jackson's message says which.
      throw new CedarAssertionException("The request body can not be read as " + type.getSimpleName(), e)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .parameter("type", type.getSimpleName());
    }
  }
}
