package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.CedarGroupExtract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerGroup extends AbstractCedarResourceWithDates<CedarGroupId> {

  private String specialGroup;
  private String createdBy;
  private String lastUpdatedBy;

  private static final Logger log = LoggerFactory.getLogger(FolderServerGroup.class);

  public FolderServerGroup() {
    super();
    this.setType(CedarResourceType.GROUP);
  }

  @JsonProperty(NodeProperty.Label.SPECIAL_GROUP)
  public String getSpecialGroup() {
    return specialGroup;
  }

  @JsonProperty(NodeProperty.Label.SPECIAL_GROUP)
  public void setSpecialGroup(String specialGroup) {
    this.specialGroup = specialGroup;
  }

  @JsonProperty(NodeProperty.Label.CREATED_BY)
  public String getCreatedBy() {
    return createdBy;
  }

  @JsonProperty(NodeProperty.Label.CREATED_BY)
  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_BY)
  public String getLastUpdatedBy() {
    return lastUpdatedBy;
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_BY)
  public void setLastUpdatedBy(String lastUpdatedBy) {
    this.lastUpdatedBy = lastUpdatedBy;
  }

  public CedarGroupExtract buildExtract() {
    return new CedarGroupExtract(getId(), getName());
  }

  @Override
  @JsonIgnore
  public CedarGroupId getResourceId() {
    return CedarGroupId.build(this.getId());
  }
}
