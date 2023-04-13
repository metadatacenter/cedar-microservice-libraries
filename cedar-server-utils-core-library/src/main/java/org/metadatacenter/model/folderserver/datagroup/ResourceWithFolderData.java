package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public interface ResourceWithFolderData {

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  boolean isUserHome();

  @JsonProperty(NodeProperty.Label.IS_USER_HOME)
  void setUserHome(boolean userHome);

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  boolean isSystem();

  @JsonProperty(NodeProperty.Label.IS_SYSTEM)
  void setSystem(boolean system);

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  boolean isRoot();

  @JsonProperty(NodeProperty.Label.IS_ROOT)
  void setRoot(boolean root);

  @JsonProperty(NodeProperty.Label.IS_OPEN)
  boolean isOpen();

  @JsonProperty(NodeProperty.Label.IS_OPEN)
  void setOpen(boolean open);

}
