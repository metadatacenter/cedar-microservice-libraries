package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithNumberOfInstances {

  @JsonProperty(NodeProperty.OnTheFly.NUMBER_OF_INSTANCES)
  long getNumberOfInstances();

  @JsonProperty(NodeProperty.OnTheFly.NUMBER_OF_INSTANCES)
  void setNumberOfInstances(long numberOfInstances);

}
