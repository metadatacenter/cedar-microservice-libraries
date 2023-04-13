package org.metadatacenter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.folderserver.datagroup.TimestampDataGroup;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.util.FolderServerNodeContext;

import java.util.Map;

public abstract class AbstractCedarResourceWithDates<T extends CedarResourceId> extends AbstractCedarResource<T> {

  protected TimestampDataGroup timestampDataGroup;

  public AbstractCedarResourceWithDates() {
    super();
    timestampDataGroup = new TimestampDataGroup();
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON_TS)
  public long getLastUpdatedOnTS() {
    return timestampDataGroup.getLastUpdatedOnTS();
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON_TS)
  public void setLastUpdatedOnTS(long lastUpdatedOnTS) {
    this.timestampDataGroup.setLastUpdatedOnTS(lastUpdatedOnTS);
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON_TS)
  public long getCreatedOnTS() {
    return timestampDataGroup.getCreatedOnTS();
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON_TS)
  public void setCreatedOnTS(long createdOnTS) {
    this.timestampDataGroup.setCreatedOnTS(createdOnTS);
  }

  @JsonProperty(NodeProperty.OnTheFly.CONTEXT)
  public Map<String, String> getContext() {
    return FolderServerNodeContext.getContext();
  }

}
