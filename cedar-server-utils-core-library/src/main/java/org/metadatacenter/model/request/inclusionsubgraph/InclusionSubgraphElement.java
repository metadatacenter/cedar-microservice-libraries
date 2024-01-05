package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InclusionSubgraphElement extends InclusionSubgraphNode {

  private Map<String, InclusionSubgraphElement> elements;

  private Map<String, InclusionSubgraphTemplate> templates;

  public InclusionSubgraphElement() {
    this.setType(CedarResourceType.ELEMENT);
  }

  protected InclusionSubgraphElement(CedarResourceType resourceType) {
    super(resourceType);
  }

  public static InclusionSubgraphElement fromFolderServerElement(FolderServerElement element) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(element);
      InclusionSubgraphElement inclusionSubgraphElement = JsonMapper.MAPPER.readValue(s, InclusionSubgraphElement.class);
      return inclusionSubgraphElement;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
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
