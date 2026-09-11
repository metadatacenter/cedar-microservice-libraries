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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
      return JsonMapper.STRICT_MAPPER.writeValueAsString(bodyNode);
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
  }

  @Override
  public CedarRequestBody mustHaveOnly(String... accepted) throws CedarException {
    if (bodyNode == null || !bodyNode.isObject()) {
      return this;
    }
    Set<String> acceptedProperties = Set.of(accepted);
    List<String> unsupported = new ArrayList<>();
    bodyNode.fieldNames().forEachRemaining(name -> {
      if (!acceptedProperties.contains(name)) {
        unsupported.add(name);
      }
    });
    if (unsupported.isEmpty()) {
      return this;
    }
    throw new CedarAssertionException("The request body carries properties this endpoint does not "
        + "accept: " + String.join(", ", unsupported))
        .errorKey(CedarErrorKey.INVALID_INPUT)
        .parameter("unsupportedProperties", unsupported)
        .parameter("acceptedProperties", List.of(accepted));
  }

  @Override
  public <T> T convert(Class<T> type) throws CedarException {
    try {
      return JsonMapper.STRICT_MAPPER.treeToValue(bodyNode, type);
    } catch (JsonProcessingException e) {
      // The body parsed as JSON, so this is a shape the endpoint does not accept: an unknown key, a
      // value of the wrong type. That is the caller's to fix, and Jackson's message says which.
      throw new CedarAssertionException("The request body can not be read as " + type.getSimpleName(), e)
          .errorKey(CedarErrorKey.INVALID_INPUT)
          .parameter("type", type.getSimpleName());
    }
  }
}
