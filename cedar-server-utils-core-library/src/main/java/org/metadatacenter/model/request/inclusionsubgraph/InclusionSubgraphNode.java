package org.metadatacenter.model.request.inclusionsubgraph;

import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.NameDescriptionIdentifierGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithUsersAndUserNamesData;
import org.metadatacenter.model.folderserver.datagroup.UserNamesDataGroup;
import org.metadatacenter.model.folderserver.datagroup.UsersDataGroup;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.InclusionSubgraphNodeOperation;
import org.metadatacenter.server.security.model.FilesystemResourceWithIdAndType;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

public class InclusionSubgraphNode extends AbstractCedarResourceWithDates implements FilesystemResourceWithIdAndType, ResourceWithUsersAndUserNamesData {

  protected NameDescriptionIdentifierGroup nameDescriptionIdentifierGroup;
  protected UsersDataGroup usersData;
  protected UserNamesDataGroup userNamesData;

  protected InclusionSubgraphNodeOperation operation = InclusionSubgraphNodeOperation.DO_NOT_UPDATE;

  public InclusionSubgraphNode() {
    super();
    this.nameDescriptionIdentifierGroup = new NameDescriptionIdentifierGroup();
    this.usersData = new UsersDataGroup();
    this.userNamesData = new UserNamesDataGroup();
  }

  protected InclusionSubgraphNode(CedarResourceType resourceType) {
    super();
    this.nameDescriptionIdentifierGroup = new NameDescriptionIdentifierGroup();
    this.usersData = new UsersDataGroup();
    this.userNamesData = new UserNamesDataGroup();
    this.setType(resourceType);
  }

  public static InclusionSubgraphNode fromNodeExtract(FolderServerResourceExtract node) {
    try {
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(node), InclusionSubgraphNode.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public String getName() {
    return nameDescriptionIdentifierGroup.getName();
  }

  @Override
  public void setName(String name) {
    nameDescriptionIdentifierGroup.setName(name);
  }

  @Override
  public String getDescription() {
    return nameDescriptionIdentifierGroup.getDescription();
  }

  @Override
  public void setDescription(String description) {
    nameDescriptionIdentifierGroup.setDescription(description);
  }

  @Override
  public String getIdentifier() {
    return nameDescriptionIdentifierGroup.getIdentifier();
  }

  @Override
  public void setIdentifier(String identifier) {
    nameDescriptionIdentifierGroup.setIdentifier(identifier);
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

  @Override
  public void setOwnedByUserName(String ownedByUserName) {
    userNamesData.setOwnedByUserName(ownedByUserName);
  }

  @Override
  public String getOwnedByUserName() {
    return userNamesData.getOwnedByUserName();
  }

  @Override
  public void setCreatedByUserName(String createdByUserName) {
    userNamesData.setCreatedByUserName(createdByUserName);
  }

  @Override
  public String getCreatedByUserName() {
    return userNamesData.getCreatedByUserName();
  }

  @Override
  public void setLastUpdatedByUserName(String lastUpdatedByUserName) {
    userNamesData.setLastUpdatedByUserName(lastUpdatedByUserName);
  }

  @Override
  public String getLastUpdatedByUserName() {
    return userNamesData.getLastUpdatedByUserName();
  }

  public CedarFilesystemResourceId getResourceId() {
    return CedarFilesystemResourceId.build(this.getId(), this.getType());
  }

  public InclusionSubgraphNodeOperation getOperation() {
    return operation;
  }

  public void setOperation(InclusionSubgraphNodeOperation operation) {
    this.operation = operation;
  }
}
