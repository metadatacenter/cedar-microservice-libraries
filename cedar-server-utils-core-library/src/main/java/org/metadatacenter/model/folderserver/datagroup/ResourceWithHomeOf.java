package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithHomeOf {

  @JsonProperty(NodeProperty.Label.HOME_OF)
  String getHomeOf();

  @JsonProperty(NodeProperty.Label.HOME_OF)
  void setHomeOf(String homeOf);

}
