package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithDerivedFromData {

  @JsonProperty(NodeProperty.Label.DERIVED_FROM)
  CedarUntypedArtifactId getDerivedFrom();

  @JsonProperty(NodeProperty.Label.DERIVED_FROM)
  void setDerivedFrom(CedarUntypedArtifactId df);

}
