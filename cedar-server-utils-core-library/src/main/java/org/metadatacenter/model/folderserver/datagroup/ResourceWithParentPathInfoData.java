package org.metadatacenter.model.folderserver.datagroup;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.List;

public interface ResourceWithParentPathInfoData {

  String getPath();

  void setPath(String path);

  String getParentPath();

  void setParentPath(String parentPath);

  @JsonProperty(NodeProperty.OnTheFly.PATH_INFO)
  List<FolderServerResourceExtract> getPathInfo();

  @JsonProperty(NodeProperty.OnTheFly.PATH_INFO)
  void setPathInfo(List<FolderServerResourceExtract> pathInfo);

}
