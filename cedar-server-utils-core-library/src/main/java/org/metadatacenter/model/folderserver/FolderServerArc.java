package org.metadatacenter.model.folderserver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.RelationLabel;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FolderServerArc {

  private String sourceId;
  private RelationLabel label;
  private String targetId;

  public FolderServerArc(String sourceId, RelationLabel label, String targetId) {
    this.sourceId = sourceId;
    this.label = label;
    this.targetId = targetId;
  }

  public String getSourceId() {
    return sourceId;
  }

  public RelationLabel getLabel() {
    return label;
  }

  public String getTargetId() {
    return targetId;
  }
}
