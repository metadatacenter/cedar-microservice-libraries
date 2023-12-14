package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class InclusionSubgraphRequest {

  @JsonProperty("@id")
  private String id;

  private Map<String, InclusionSubgraphElement> elements;

  private Map<String, InclusionSubgraphTemplate> templates;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Map<String, InclusionSubgraphElement> getElements() {
    return elements;
  }

  public void setElements(Map<String, InclusionSubgraphElement> elements) {
    this.elements = elements;
  }

  public Map<String, InclusionSubgraphTemplate> getTemplates() {
    return templates;
  }

  public void setTemplates(Map<String, InclusionSubgraphTemplate> templates) {
    this.templates = templates;
  }
}
