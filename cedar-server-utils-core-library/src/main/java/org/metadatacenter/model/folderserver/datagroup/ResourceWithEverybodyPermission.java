package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;

public interface ResourceWithEverybodyPermission {

  @JsonProperty(NodeProperty.Label.EVERYBODY_PERMISSION)
  NodeSharePermission getEverybodyPermission();

  @JsonProperty(NodeProperty.Label.EVERYBODY_PERMISSION)
  void setEverybodyPermission(NodeSharePermission permission);

}
