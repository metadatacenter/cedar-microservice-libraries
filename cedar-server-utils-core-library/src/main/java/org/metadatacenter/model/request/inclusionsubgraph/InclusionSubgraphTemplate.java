package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InclusionSubgraphTemplate extends InclusionSubgraphNode {

  public InclusionSubgraphTemplate() {
    this.setType(CedarResourceType.TEMPLATE);
  }

  protected InclusionSubgraphTemplate(CedarResourceType resourceType) {
    super(resourceType);
  }

  public static InclusionSubgraphTemplate fromFolderServerTemplate(FolderServerTemplate template) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(template);
      InclusionSubgraphTemplate inclusionSubgraphTemplate = JsonMapper.MAPPER.readValue(s, InclusionSubgraphTemplate.class);
      return inclusionSubgraphTemplate;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

}
