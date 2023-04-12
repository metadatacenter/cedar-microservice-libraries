package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithVersionData {

  @JsonProperty(NodeProperty.Label.VERSION)
  ResourceVersion getVersion();

  @JsonProperty(NodeProperty.Label.VERSION)
  void setVersion(String versionString);

  @JsonProperty(NodeProperty.Label.IS_LATEST_VERSION)
  Boolean isLatestVersion();

  @JsonProperty(NodeProperty.Label.IS_LATEST_VERSION)
  void setLatestVersion(Boolean latestVersion);

  @JsonProperty(NodeProperty.Label.IS_LATEST_DRAFT_VERSION)
  Boolean isLatestDraftVersion();

  @JsonProperty(NodeProperty.Label.IS_LATEST_DRAFT_VERSION)
  void setLatestDraftVersion(Boolean latestDraftVersion);

  @JsonProperty(NodeProperty.Label.IS_LATEST_PUBLISHED_VERSION)
  Boolean isLatestPublishedVersion();

  @JsonProperty(NodeProperty.Label.IS_LATEST_PUBLISHED_VERSION)
  void setLatestPublishedVersion(Boolean latestPublishedVersion);

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  BiboStatus getPublicationStatus();

  @JsonProperty(NodeProperty.Label.PUBLICATION_STATUS)
  void setPublicationStatus(String s);

}
