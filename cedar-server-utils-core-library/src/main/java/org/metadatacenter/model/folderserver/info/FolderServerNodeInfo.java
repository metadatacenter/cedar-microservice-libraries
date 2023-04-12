package org.metadatacenter.model.folderserver.info;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.*;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.datagroup.*;
import org.metadatacenter.server.model.provenance.ProvenanceTime;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerNodeInfo implements ResourceWithVersionData, ResourceWithUsersData {

  protected BaseDataGroup baseData;
  protected NameDescriptionIdentifierGroup nameDescriptionIdentifierData;
  protected VersionDataGroup versionData;
  protected PreviousVersionGroup previousVersionData;
  protected DerivedFromGroup derivedFromData;
  protected IsBasedOnGroup isBasedOnData;
  protected UsersDataGroup usersData;
  protected FolderDataGroup folderData;

  protected String path;
  protected boolean isOpen;
  protected NodeSharePermission everybodyPermission;

  private FolderServerNodeInfo() {
    this.baseData = new BaseDataGroup();
    this.nameDescriptionIdentifierData = new NameDescriptionIdentifierGroup();
    this.versionData = new VersionDataGroup();
    this.previousVersionData = new PreviousVersionGroup();
    this.derivedFromData = new DerivedFromGroup();
    this.isBasedOnData = new IsBasedOnGroup();
    this.usersData = new UsersDataGroup();
    this.folderData = new FolderDataGroup();
  }

  public static FolderServerNodeInfo fromNode(FileSystemResource node) {
    try {
      FolderServerNodeInfo info = JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(node), FolderServerNodeInfo.class);
      info.setType(node.getType());
      if (node.getPathInfo() != null && node.getPathInfo().size() > 1) {
        info.setParentFolderId(node.getPathInfo().get(node.getPathInfo().size()-2).getId());
      }
      return info;
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.Label.ID)
  public String getId() {
    return baseData.getId();
  }

  @JsonProperty(NodeProperty.Label.ID)
  public void setId(String id) {
    baseData.setId(id);
  }

  @JsonProperty(NodeProperty.Label.RESOURCE_TYPE)
  public CedarResourceType getType() {
    return baseData.getResourceType();
  }

  @JsonProperty(NodeProperty.Label.RESOURCE_TYPE)
  public void setType(CedarResourceType resourceType) {
    baseData.setResourceType(resourceType);
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON)
  public ProvenanceTime getCreatedOn() {
    return baseData.getCreatedOn();
  }

  @JsonProperty(NodeProperty.Label.CREATED_ON)
  public void setCreatedOn(ProvenanceTime createdOn) {
    baseData.setCreatedOn(createdOn);
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON)
  public ProvenanceTime getLastUpdatedOn() {
    return baseData.getLastUpdatedOn();
  }

  @JsonProperty(NodeProperty.Label.LAST_UPDATED_ON)
  public void setLastUpdatedOn(ProvenanceTime lastUpdatedOn) {
    baseData.setLastUpdatedOn(lastUpdatedOn);
  }

  @JsonProperty(NodeProperty.Label.NAME)
  public String getName() {
    return nameDescriptionIdentifierData.getName();
  }

  @JsonProperty(NodeProperty.Label.NAME)
  public void setName(String name) {
    nameDescriptionIdentifierData.setName(name);
  }

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  public String getDescription() {
    return nameDescriptionIdentifierData.getDescription();
  }

  @JsonProperty(NodeProperty.Label.DESCRIPTION)
  public void setDescription(String description) {
    nameDescriptionIdentifierData.setDescription(description);
  }

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  public String getIdentifier() {
    return nameDescriptionIdentifierData.getIdentifier();
  }

  @JsonProperty(NodeProperty.Label.IDENTIFIER)
  public void setIdentifier(String identifier) {
    nameDescriptionIdentifierData.setIdentifier(identifier);
  }

  @JsonProperty(NodeProperty.Label.PREVIOUS_VERSION)
  public String getPreviousVersion() {
    return previousVersionData.getPreviousVersion() == null ? null : previousVersionData.getPreviousVersion().getId();
  }

  @JsonProperty(NodeProperty.Label.PREVIOUS_VERSION)
  public void setPreviousVersion(CedarUntypedSchemaArtifactId pv) {
    previousVersionData.setPreviousVersion(pv);
  }

  @JsonIgnore
  public CedarUntypedSchemaArtifactId getPreviousVersionId() {
    return previousVersionData.getPreviousVersion();
  }

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public BiboStatus getPublicationStatus() {
    return versionData.getPublicationStatus();
  }

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public void setPublicationStatus(String s) {
    versionData.setPublicationStatus(BiboStatus.forValue(s));
  }

  @JsonProperty(NodeProperty.Label.DERIVED_FROM)
  public String getDerivedFrom() {
    return derivedFromData.getDerivedFrom() == null ? null : derivedFromData.getDerivedFrom().getId();
  }

  @JsonProperty(NodeProperty.Label.DERIVED_FROM)
  public void setDerivedFrom(CedarUntypedArtifactId df) {
    derivedFromData.setDerivedFrom(df);
  }

  @JsonIgnore
  public CedarUntypedArtifactId getDerivedFromId() {
    return derivedFromData.getDerivedFrom();
  }

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public String getIsBasedOn() {
    return isBasedOnData.getIsBasedOn() == null ? null : isBasedOnData.getIsBasedOn().getId();
  }

  @JsonProperty(NodeProperty.Label.IS_BASED_ON)
  public void setIsBasedOn(CedarTemplateId isBasedOn) {
    isBasedOnData.setIsBasedOn(isBasedOn);
  }

  @JsonIgnore
  public CedarTemplateId getIsBasedOnId() {
    return isBasedOnData.getIsBasedOn();
  }

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  public boolean getIsRoot() {
    return folderData.isRoot();
  }

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  public void setIsRoot(boolean isRoot) {
    folderData.setRoot(isRoot);
  }

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  public boolean getIsSystem() {
    return folderData.isSystem();
  }

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  public void setIsSystem(boolean isSystem) {
    folderData.setSystem(isSystem);
  }

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  public boolean getIsUserHome() {
    return folderData.isUserHome();
  }

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  public void setIsUserHome(boolean isUserHome) {
    folderData.setUserHome(isUserHome);
  }

  @JsonProperty(NodeProperty.Label.PARENT_FOLDER_ID)
  public String getParentFolderId() {return folderData.getParentFolderId();}

  @JsonProperty(NodeProperty.Label.PARENT_FOLDER_ID)
  public void setParentFolderId(String parentFolderId) {folderData.setParentFolderId(parentFolderId);}

  @JsonProperty(NodeProperty.Label.IS_OPEN)
  public boolean getIsOpen() {
    return isOpen;
  }

  @JsonProperty(NodeProperty.Label.IS_OPEN)
  public void setIsOpen(boolean isOpen) {
    this.isOpen = isOpen;
  }

  @JsonProperty(NodeProperty.Label.EVERYBODY_PERMISSION)
  public NodeSharePermission getEverybodyPermission() {
    return everybodyPermission;
  }

  @JsonProperty(NodeProperty.Label.EVERYBODY_PERMISSION)
  public void setEverybodyPermission(NodeSharePermission everybodyPermission) {
    this.everybodyPermission = everybodyPermission;
  }

  @Override
  public ResourceVersion getVersion() {
    return versionData.getVersion();
  }

  @Override
  public void setVersion(String versionString) {
    versionData.setVersion(ResourceVersion.forValue(versionString));
  }

  @Override
  public Boolean isLatestVersion() {
    return versionData.isLatestVersion();
  }

  @Override
  public void setLatestVersion(Boolean latestVersion) {
    versionData.setLatestVersion(latestVersion);
  }

  @Override
  public Boolean isLatestDraftVersion() {
    return versionData.isLatestDraftVersion();
  }

  @Override
  public void setLatestDraftVersion(Boolean latestDraftVersion) {
    versionData.setLatestDraftVersion(latestDraftVersion);
  }

  @Override
  public Boolean isLatestPublishedVersion() {
    return versionData.isLatestPublishedVersion();
  }

  @Override
  public void setLatestPublishedVersion(Boolean latestPublishedVersion) {
    versionData.setLatestPublishedVersion(latestPublishedVersion);
  }

  @Override
  public String getOwnedBy() {
    return usersData.getOwnedBy();
  }

  @Override
  public void setOwnedBy(String ownedBy) {
    usersData.setOwnedBy(ownedBy);
  }

  @Override
  public String getCreatedBy() {
    return usersData.getCreatedBy();
  }

  @Override
  public void setCreatedBy(String createdBy) {
    usersData.setCreatedBy(createdBy);
  }

  @Override
  public String getLastUpdatedBy() {
    return usersData.getLastUpdatedBy();
  }

  @Override
  public void setLastUpdatedBy(String lastUpdatedBy) {
    usersData.setLastUpdatedBy(lastUpdatedBy);
  }

}
