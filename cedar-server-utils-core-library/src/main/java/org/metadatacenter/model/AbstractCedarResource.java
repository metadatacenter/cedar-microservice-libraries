package org.metadatacenter.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.folderserver.datagroup.BaseDataGroup;
import org.metadatacenter.model.folderserver.datagroup.NameDescriptionIdentifierGroup;
import org.metadatacenter.server.model.provenance.ProvenanceTime;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public abstract class AbstractCedarResource<T extends CedarResourceId> implements CedarResource<T> {

  protected BaseDataGroup baseDataGroup;
  protected NameDescriptionIdentifierGroup nameDescriptionIdentifierGroup;

  public AbstractCedarResource() {
    this.baseDataGroup = new BaseDataGroup();
    this.nameDescriptionIdentifierGroup = new NameDescriptionIdentifierGroup();
  }

  @JsonProperty(NodeProperty.Label.ID)
  public String getId() {
    return baseDataGroup.getId();
  }

  @JsonProperty(NodeProperty.Label.ID)
  public void setId(String id) {
    this.baseDataGroup.setId(id);
  }

  @JsonProperty(NodeProperty.Label.RESOURCE_TYPE)
  public CedarResourceType getType() {
    return baseDataGroup.getResourceType();
  }

  @JsonProperty(NodeProperty.Label.RESOURCE_TYPE)
  public void setType(CedarResourceType resourceType) {
    this.baseDataGroup.setResourceType(resourceType);
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON)
  public ProvenanceTime getCreatedOn() {
    return baseDataGroup.getCreatedOn();
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON)
  public void setCreatedOn(ProvenanceTime createdOn) {
    this.baseDataGroup.setCreatedOn(createdOn);
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON)
  public ProvenanceTime getLastUpdatedOn() {
    return baseDataGroup.getLastUpdatedOn();
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON)
  public void setLastUpdatedOn(ProvenanceTime lastUpdatedOn) {
    this.baseDataGroup.setLastUpdatedOn(lastUpdatedOn);
  }

  @JsonProperty(NodeProperty.Label.NAME)
  public String getName() {
    return this.nameDescriptionIdentifierGroup.getName();
  }

  @JsonProperty(NodeProperty.Label.NAME)
  public void setName(String name) {
    this.nameDescriptionIdentifierGroup.setName(name);
  }

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  public String getDescription() {
    return this.nameDescriptionIdentifierGroup.getDescription();
  }

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  public void setDescription(String description) {
    this.nameDescriptionIdentifierGroup.setDescription(description);
  }

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  public String getIdentifier() {
    return this.nameDescriptionIdentifierGroup.getIdentifier();
  }

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  public void setIdentifier(String identifier) {
    this.nameDescriptionIdentifierGroup.setIdentifier(identifier);
  }

  @JsonProperty(NodeProperty.Label.SOURCE_HASH)
  public String getSourceHash() {
    return this.baseDataGroup.getSourceHash();
  }

  @JsonProperty(NodeProperty.Label.SOURCE_HASH)
  public void setSourceHash(String sourceHash) {
    this.baseDataGroup.setSourceHash(sourceHash);
  }

}
