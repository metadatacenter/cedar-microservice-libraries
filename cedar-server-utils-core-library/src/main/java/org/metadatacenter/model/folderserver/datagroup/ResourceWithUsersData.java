package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithUsersData {

  @JsonProperty(NodeProperty.Label.OWNED_BY)
  String getOwnedBy();

  @JsonProperty(NodeProperty.Label.OWNED_BY)
  void setOwnedBy(String ownedBy);

  @JsonProperty(NodeProperty.Label.CREATED_BY)
  String getCreatedBy();

  @JsonProperty(NodeProperty.Label.CREATED_BY)
  void setCreatedBy(String createdBy);

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_BY)
  String getLastUpdatedBy();

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_BY)
  void setLastUpdatedBy(String lastUpdatedBy);

}
