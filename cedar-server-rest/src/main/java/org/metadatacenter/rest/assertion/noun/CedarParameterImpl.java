package org.metadatacenter.rest.assertion.noun;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.rest.context.CedarParameterSource;

public class CedarParameterImpl extends CedarParameterNoun {

  private final String name;
  private final CedarParameterSource source;
  private JsonNode jsonNode;

  public CedarParameterImpl(String name, CedarParameterSource source) {
    this.name = name;
    this.source = source;
  }

  public void setJsonNode(JsonNode jsonNode) {
    this.jsonNode = jsonNode;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public CedarParameterSource getSource() {
    return source;
  }

  @Override
  public String stringValue() {
    if (jsonNode != null && !jsonNode.isMissingNode() && !jsonNode.isNull()) {
      return getStringValue();
    } else {
      return null;
    }
  }

  @Override
  public boolean booleanValue() {
    if (jsonNode != null && !jsonNode.isMissingNode() && !jsonNode.isNull()) {
      return jsonNode.asBoolean();
    } else {
      return false;
    }
  }

  protected String getStringValue() {
    if (trimmed) {
      return jsonNode.asText().trim();
    } else {
      return jsonNode.asText();
    }
  }

  @Override
  public boolean isNull() {
    return isMissing() || jsonNode.isNull();
  }

  @Override
  public boolean isPresentAndNull() {
    return jsonNode != null && jsonNode.isNull();
  }

  @Override
  public boolean isMissing() {
    return jsonNode == null || jsonNode.isMissingNode();
  }

  @Override
  public boolean isEmpty() {
    return isNull() || stringValue().trim().isEmpty();
  }

}
