package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithNameDescriptionIdentifierData {

  @JsonProperty(NodeProperty.Label.NAME)
  String getName();

  @JsonProperty(NodeProperty.Label.NAME)
  void setName(String name);

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  String getDescription();

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  void setDescription(String description);

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  String getIdentifier();

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  void setIdentifier(String identifier);

}
