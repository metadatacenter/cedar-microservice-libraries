package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithOpenFlag;
import org.metadatacenter.model.folderserver.datagroup.VersionDataGroup;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

public abstract class FolderServerSchemaArtifactReport extends FolderServerArtifactReport implements FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus, ResourceWithOpenFlag {

  private VersionDataGroup versionData;

  public FolderServerSchemaArtifactReport(CedarResourceType resourceType) {
    super(resourceType);
    versionData = new VersionDataGroup();
  }

  public static FolderServerSchemaArtifactReport fromResource(FolderServerArtifact resource) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(resource);
      return JsonMapper.MAPPER.readValue(s, FolderServerSchemaArtifactReport.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public BiboStatus getPublicationStatus() {
    return versionData.getPublicationStatus();
  }

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  public void setPublicationStatus(String s) {
    versionData.setPublicationStatus(BiboStatus.forValue(s));
  }

  public CedarSchemaArtifactId getResourceId() {
    return CedarSchemaArtifactId.build(this.getId(), this.getType());
  }

}
