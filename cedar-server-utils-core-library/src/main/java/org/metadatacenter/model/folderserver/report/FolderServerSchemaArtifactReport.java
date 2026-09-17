package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithVersionData;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithOpenFlag;
import org.metadatacenter.model.folderserver.datagroup.VersionDataGroup;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class FolderServerSchemaArtifactReport extends FolderServerArtifactReport implements FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus, ResourceWithOpenFlag, ResourceWithVersionData {

  private static final Logger log = LoggerFactory.getLogger(FolderServerSchemaArtifactReport.class);

  private VersionDataGroup versionData;

  public FolderServerSchemaArtifactReport(CedarResourceType resourceType) {
    super(resourceType);
    versionData = new VersionDataGroup();
  }

  public static FolderServerSchemaArtifactReport fromResource(FolderServerArtifact resource) {
    try {
      String s = JsonMapper.STRICT_MAPPER.writeValueAsString(resource);
      return JsonMapper.TOLERANT_MAPPER.readValue(s, FolderServerSchemaArtifactReport.class);
    } catch (IOException e) {
      log.error("Error while converting the artifact to a schema artifact report", e);
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

  @Override public ResourceVersion getVersion() { return versionData.getVersion(); }
  @Override public void setVersion(String version) { versionData.setVersion(ResourceVersion.forValue(version)); }
  @Override public Boolean isLatestVersion() { return versionData.isLatestVersion(); }
  @Override public void setLatestVersion(Boolean latest) { versionData.setLatestVersion(latest); }
  @Override public Boolean isLatestDraftVersion() { return versionData.isLatestDraftVersion(); }
  @Override public void setLatestDraftVersion(Boolean latest) { versionData.setLatestDraftVersion(latest); }
  @Override public Boolean isLatestPublishedVersion() { return versionData.isLatestPublishedVersion(); }
  @Override public void setLatestPublishedVersion(Boolean latest) { versionData.setLatestPublishedVersion(latest); }

  public CedarSchemaArtifactId getResourceId() {
    return CedarSchemaArtifactId.build(this.getId(), this.getType());
  }

}
