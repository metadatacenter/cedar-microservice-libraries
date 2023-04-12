package org.metadatacenter.rest.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.assertion.noun.CedarParameter;
import org.metadatacenter.rest.assertion.noun.CedarParameterImpl;
import org.metadatacenter.rest.assertion.noun.CedarRequestBody;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRequestJsonBody implements CedarRequestBody {

  protected static final Logger log = LoggerFactory.getLogger(HttpRequestJsonBody.class);

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
  public <T> T convert(Class<T> type) throws CedarAssertionException {
    T object;
    try {
      object = JsonMapper.MAPPER.treeToValue(bodyNode, type);
    } catch (JsonProcessingException e) {
      log.warn("Error while processing json", e);
      throw new CedarAssertionException(e);
    }
    return object;
  }
}
