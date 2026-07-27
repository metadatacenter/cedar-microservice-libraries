package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InclusionSubgraphTemplate extends InclusionSubgraphNode {

  private static final Logger log = LoggerFactory.getLogger(InclusionSubgraphTemplate.class);

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
      log.error("Error while converting the template to an inclusion subgraph template", e);
    }
    return null;
  }

}
