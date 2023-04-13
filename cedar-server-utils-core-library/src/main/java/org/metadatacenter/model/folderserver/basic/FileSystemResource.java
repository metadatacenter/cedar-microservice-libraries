package org.metadatacenter.model.folderserver.basic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.*;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.security.model.FilesystemResourceWithIdAndType;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerFolder.class, name = CedarResourceType.Types.FOLDER),
    @JsonSubTypes.Type(value = FolderServerField.class, name = CedarResourceType.Types.FIELD),
    @JsonSubTypes.Type(value = FolderServerElement.class, name = CedarResourceType.Types.ELEMENT),
    @JsonSubTypes.Type(value = FolderServerTemplate.class, name = CedarResourceType.Types.TEMPLATE),
    @JsonSubTypes.Type(value = FolderServerInstance.class, name = CedarResourceType.Types.INSTANCE)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class FileSystemResource extends AbstractCedarResourceWithDates implements ResourceWithOpenFlag,
    FilesystemResourceWithIdAndType, ResourceWithUsersAndUserNamesData, ResourceWithEverybodyPermission, ResourceWithParentPathInfoData {

  protected NameDescriptionIdentifierGroup nameDescriptionIdentifierGroup;
  protected ParentPathInfoGroup parentPathInfoGroup;
  protected UsersDataGroup usersData;
  protected UserNamesDataGroup userNamesData;
  protected NodeSharePermission everybodyPermission;
  protected boolean open;

  protected FileSystemResource(CedarResourceType resourceType) {
    super();
    this.nameDescriptionIdentifierGroup = new NameDescriptionIdentifierGroup();
    this.parentPathInfoGroup = new ParentPathInfoGroup();
    this.usersData = new UsersDataGroup();
    this.userNamesData = new UserNamesDataGroup();
    this.setType(resourceType);
  }

  public static FileSystemResource fromNodeExtract(FolderServerResourceExtract node) {
    try {
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(node), FileSystemResource.class);
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
  public String getPath() {
    return parentPathInfoGroup.getPath();
  }

  @Override
  public void setPath(String path) {
    parentPathInfoGroup.setPath(path);
  }

  @Override
  public String getParentPath() {
    return parentPathInfoGroup.getParentPath();
  }

  @Override
  public void setParentPath(String parentPath) {
    parentPathInfoGroup.setParentPath(parentPath);
  }

  @Override
  public List<FolderServerResourceExtract> getPathInfo() {
    return parentPathInfoGroup.getPathInfo();
  }

  @Override
  public void setPathInfo(List<FolderServerResourceExtract> pathInfo) {
    parentPathInfoGroup.setPathInfo(pathInfo);
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

  @Override
  public NodeSharePermission getEverybodyPermission() {
    return everybodyPermission;
  }

  @Override
  public void setEverybodyPermission(NodeSharePermission everybodyPermission) {
    this.everybodyPermission = everybodyPermission;
  }

  public void setCreatedByTotal(CedarUserId createdBy) {
    setCreatedBy(createdBy.getId());
    setOwnedBy(createdBy.getId());
    setLastUpdatedBy(createdBy.getId());
  }

  public CedarFilesystemResourceId getResourceId() {
    return CedarFilesystemResourceId.build(this.getId(), this.getType());
  }
  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void setOpen(boolean open) {
    this.open = open;
  }
}
