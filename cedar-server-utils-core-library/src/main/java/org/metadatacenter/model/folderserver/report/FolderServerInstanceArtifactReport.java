package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.datagroup.IsBasedOnGroup;
import org.metadatacenter.model.folderserver.datagroup.ResourceWithIsBasedOn;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class FolderServerInstanceArtifactReport extends FolderServerArtifactReport implements ResourceWithIsBasedOn {

  private static final Logger log = LoggerFactory.getLogger(FolderServerInstanceArtifactReport.class);

  private IsBasedOnGroup isBasedOnGroup;
  private FolderServerTemplateExtract isBasedOnExtract;

  public FolderServerInstanceArtifactReport(CedarResourceType resourceType) {
    super(resourceType);
    isBasedOnGroup = new IsBasedOnGroup();
  }

  public static FolderServerInstanceArtifactReport fromResource(FolderServerArtifact resource) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(resource);
      return JsonMapper.MAPPER.readValue(s, FolderServerInstanceArtifactReport.class);
    } catch (IOException e) {
      log.error("Error while converting the artifact to an instance artifact report", e);
    }
    return null;
  }

  @Override
  public CedarTemplateId getIsBasedOn() {
    return isBasedOnGroup.getIsBasedOn();
  }

  @Override
  public void setIsBasedOn(CedarTemplateId isBasedOn) {
    isBasedOnGroup.setIsBasedOn(isBasedOn);
  }

  @JsonProperty(NodeProperty.OnTheFly.IS_BASED_ON)
  public FolderServerTemplateExtract getIsBasedOnExtract() {
    return isBasedOnExtract;
  }

  @JsonProperty(NodeProperty.OnTheFly.IS_BASED_ON)
  public void setIsBasedOnExtract(FolderServerTemplateExtract isBasedOnExtract) {
    this.isBasedOnExtract = isBasedOnExtract;
  }

}
