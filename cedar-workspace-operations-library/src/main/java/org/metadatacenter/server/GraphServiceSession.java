package org.metadatacenter.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;

import java.util.List;

public interface GraphServiceSession {

  List<FolderServerArc> getOutgoingArcs(CedarResourceId resourceId);

  List<FolderServerArc> getIncomingArcs(CedarResourceId resourceId);

  FolderServerUser createUser(JsonNode node);

  FolderServerGroup createGroup(JsonNode node);

  FileSystemResource createFilesystemResource(JsonNode node);

  boolean createArc(CedarResourceId sourceId, RelationLabel relationLabel, CedarResourceId targetId);
}
