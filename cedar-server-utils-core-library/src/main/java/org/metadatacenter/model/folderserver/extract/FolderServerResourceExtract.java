package org.metadatacenter.model.folderserver.extract;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.model.AbstractCedarResourceExtract;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.datagroup.*;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.FilesystemResourceWithIdAndType;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerFolderExtract.class, name = CedarResourceType.Types.FOLDER),
    @JsonSubTypes.Type(value = FolderServerFieldExtract.class, name = CedarResourceType.Types.FIELD),
    @JsonSubTypes.Type(value = FolderServerElementExtract.class, name = CedarResourceType.Types.ELEMENT),
    @JsonSubTypes.Type(value = FolderServerTemplateExtract.class, name = CedarResourceType.Types.TEMPLATE),
    @JsonSubTypes.Type(value = FolderServerTemplateInstanceExtract.class, name = CedarResourceType.Types.INSTANCE)
})
public abstract class FolderServerResourceExtract extends AbstractCedarResourceExtract implements FilesystemResourceWithIdAndType,
    ResourceWithUserNamesData, ResourceWithUsersData, ResourceWithEverybodyPermission {

  protected UsersDataGroup usersData;
  protected UserNamesDataGroup userNamesData;
  protected boolean activeUserCanRead = true;

  protected NodeSharePermission everybodyPermission;

  protected Boolean isOpen;

  protected Boolean isOpenImplicitly;

  protected FolderServerResourceExtract(CedarResourceType resourceType) {
    super();
    this.usersData = new UsersDataGroup();
    this.userNamesData = new UserNamesDataGroup();
    this.setType(resourceType);
  }

  public static FolderServerResourceExtract fromNode(FileSystemResource node) {
    try {
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(node), FolderServerResourceExtract.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static FolderServerResourceExtract fromNodeInfo(FolderServerNodeInfo info) {
    try {
      return JsonMapper.MAPPER.readValue(JsonMapper.MAPPER.writeValueAsString(info), FolderServerResourceExtract.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
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

  @JsonProperty(NodeProperty.OnTheFly.ACTIVE_USER_CAN_READ)
  public boolean isActiveUserCanRead() {
    return activeUserCanRead;
  }

  @JsonProperty(NodeProperty.OnTheFly.ACTIVE_USER_CAN_READ)
  public void setActiveUserCanRead(boolean activeUserCanRead) {
    this.activeUserCanRead = activeUserCanRead;
  }

  @Override
  public NodeSharePermission getEverybodyPermission() {
    return everybodyPermission;
  }

  @Override
  public void setEverybodyPermission(NodeSharePermission everybodyPermission) {
    this.everybodyPermission = everybodyPermission;
  }

  public Boolean getIsOpen() {
    return isOpen;
  }

  public void setIsOpen(Boolean isOpen) {
    this.isOpen = isOpen;
  }

  public Boolean getIsOpenImplicitly() {
    return isOpenImplicitly;
  }

  public void setIsOpenImplicitly(Boolean openImplicitly) {
    isOpenImplicitly = openImplicitly;
  }

  public static FolderServerResourceExtract forType(CedarResourceType t) {
    return switch (t) {
      case FOLDER -> new FolderServerFolderExtract();
      case FIELD -> new FolderServerFieldExtract();
      case ELEMENT -> new FolderServerElementExtract();
      case TEMPLATE -> new FolderServerTemplateExtract();
      case INSTANCE -> new FolderServerTemplateInstanceExtract();
      default -> null;
    };
  }

  public static FolderServerTemplateExtract anonymous(FolderServerTemplateExtract resource) {
    FolderServerTemplateExtract anon = new FolderServerTemplateExtract();
    anon.setId(resource.getId());
    anon.setActiveUserCanRead(false);
    return anon;
  }

  public static FolderServerArtifactExtract anonymous(FolderServerArtifactExtract resource) {
    FolderServerArtifactExtract anon =
        (FolderServerArtifactExtract) FolderServerResourceExtract.forType(resource.getType());
    anon.setId(resource.getId());
    anon.setActiveUserCanRead(false);
    return anon;
  }

}
