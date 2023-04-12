package org.metadatacenter.model.folderserver.report;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerArtifactCurrentUserReport;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerCategoryExtract;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "resourceType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FolderServerFieldReport.class, name = CedarResourceType.Types.FIELD),
    @JsonSubTypes.Type(value = FolderServerElementReport.class, name = CedarResourceType.Types.ELEMENT),
    @JsonSubTypes.Type(value = FolderServerTemplateReport.class, name = CedarResourceType.Types.TEMPLATE),
    @JsonSubTypes.Type(value = FolderServerInstanceReport.class, name = CedarResourceType.Types.INSTANCE)
})
public abstract class FolderServerArtifactReport extends FolderServerArtifactCurrentUserReport {

  private FolderServerArtifactExtract derivedFromExtract;
  private List<FolderServerArtifactExtract> versions;
  private List<List<FolderServerCategoryExtract>> categories;
  private String trustedBy;

  public FolderServerArtifactReport(CedarResourceType resourceType) {
    super(resourceType);
    this.versions = new ArrayList<>();
    this.categories = new ArrayList<>();
  }

  public static FolderServerArtifactReport fromResource(FolderServerArtifact resource) {
    try {
      String s = JsonMapper.MAPPER.writeValueAsString(resource);
      return JsonMapper.MAPPER.readValue(s, FolderServerArtifactReport.class);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  @JsonProperty(NodeProperty.OnTheFly.DERIVED_FROM)
  public FolderServerArtifactExtract getDerivedFromExtract() {
    return derivedFromExtract;
  }

  @JsonProperty(NodeProperty.OnTheFly.DERIVED_FROM)
  public void setDerivedFromExtract(FolderServerArtifactExtract derivedFromExtract) {
    this.derivedFromExtract = derivedFromExtract;
  }

  @JsonProperty(NodeProperty.OnTheFly.VERSIONS)
  public List<FolderServerArtifactExtract> getVersions() {
    return versions;
  }

  @JsonProperty(NodeProperty.OnTheFly.VERSIONS)
  public void setVersions(List<FolderServerArtifactExtract> versions) {
    this.versions = versions;
  }

  @JsonProperty(NodeProperty.OnTheFly.CATEGORIES)
  public List<List<FolderServerCategoryExtract>> getCategories() {
    return categories;
  }

  @JsonProperty(NodeProperty.OnTheFly.CATEGORIES)
  public void setCategories(List<List<FolderServerCategoryExtract>> categories) {
    this.categories = categories;
  }

  @JsonProperty("trustedBy")
  public String getTrustedBy() {
    return trustedBy;
  }

  @JsonProperty("trustedBy")
  public void setTrustedBy(String trustedBy) {
    this.trustedBy = trustedBy;
  }

}
