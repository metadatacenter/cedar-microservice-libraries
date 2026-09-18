package org.metadatacenter.model.request.inclusionsubgraph;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.AbstractCedarResourceWithDates;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.datagroup.NameDescriptionIdentifierGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithUsersAndUserNamesData;
import org.metadatacenter.model.folderserver.datagroup.UserNamesDataGroup;
import org.metadatacenter.model.folderserver.datagroup.UsersDataGroup;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.InclusionSubgraphNodeOperation;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.FilesystemResourceWithIdAndType;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InclusionSubgraphNode extends AbstractCedarResourceWithDates implements FilesystemResourceWithIdAndType, ResourceWithUsersAndUserNamesData {

  private static final Logger log = LoggerFactory.getLogger(InclusionSubgraphNode.class);

  protected NameDescriptionIdentifierGroup nameDescriptionIdentifierGroup;
  protected UsersDataGroup usersData;
  protected UserNamesDataGroup userNamesData;

  protected InclusionSubgraphNodeOperation operation = InclusionSubgraphNodeOperation.DO_NOT_UPDATE;

  protected BiboStatus publicationStatus;

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
      return JsonMapper.STRICT_MAPPER.readValue(JsonMapper.STRICT_MAPPER.writeValueAsString(node), InclusionSubgraphNode.class);
    } catch (IOException e) {
      log.error("Error while converting the resource extract to an inclusion subgraph node", e);
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

  // Internal authorization helper; previews are sent back as strict request bodies by the selector.
  @JsonIgnore
  public CedarFilesystemResourceId getResourceId() {
    return CedarFilesystemResourceId.build(this.getId(), this.getType());
  }

  public InclusionSubgraphNodeOperation getOperation() {
    return operation;
  }

  public void setOperation(InclusionSubgraphNodeOperation operation) {
    this.operation = operation;
  }

  /**
   * The publication status this artifact holds in the graph.
   *
   * <p>Propagation refuses a published target, so a selector can read this to offer no tick for one
   * rather than let a caller choose an artifact the update will reject. A draft may still be refused
   * on other grounds: the caller may lack write access to it, and a structural change into a template
   * that already has instances needs a new version of that template.
   */
  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public BiboStatus getPublicationStatus() {
    return publicationStatus;
  }

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public void setPublicationStatus(String s) {
    this.publicationStatus = BiboStatus.forValue(s);
  }
}
