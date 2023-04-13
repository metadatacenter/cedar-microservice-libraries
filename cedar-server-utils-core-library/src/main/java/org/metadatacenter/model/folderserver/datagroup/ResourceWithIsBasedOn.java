package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithIsBasedOn {

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  CedarTemplateId getIsBasedOn();

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  void setIsBasedOn(CedarTemplateId isBasedOn);

}
