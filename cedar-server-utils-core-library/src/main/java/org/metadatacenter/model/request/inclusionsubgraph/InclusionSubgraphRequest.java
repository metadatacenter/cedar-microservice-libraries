package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * The artifact that changed, and the elements and templates to consider.
 *
 * <p>The two subgraph commands read this body strictly, so the schema generated from this type
 * states that it accepts nothing else.
 */
@Schema(name = "InclusionSubgraphRequest",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
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
