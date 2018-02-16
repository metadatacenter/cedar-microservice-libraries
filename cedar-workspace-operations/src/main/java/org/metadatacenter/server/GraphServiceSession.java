package org.metadatacenter.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.FolderServerGroup;
import org.metadatacenter.model.folderserver.FolderServerUser;

import java.util.List;

public interface GraphServiceSession {

  List<FolderServerArc> getOutgoingArcs(String nodeId);

  List<FolderServerArc> getIncomingArcs(String nodeId);

  FolderServerUser createUser(JsonNode node);

  FolderServerGroup createGroup(JsonNode node);
}
