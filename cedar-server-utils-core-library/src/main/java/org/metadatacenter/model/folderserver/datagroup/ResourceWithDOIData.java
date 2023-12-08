package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithDOIData {

  @JsonProperty(NodeProperty.Label.DOI)
  String getDOI();

  @JsonProperty(NodeProperty.Label.DOI)
  void setDOI(String doi);

}
