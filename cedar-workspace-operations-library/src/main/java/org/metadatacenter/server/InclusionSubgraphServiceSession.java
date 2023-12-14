package org.metadatacenter.server;

import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;

import java.util.List;

public interface InclusionSubgraphServiceSession {


  boolean updateInclusionArcs(CedarResourceId sourceId, List<String> includedIds);

  List<FolderServerTemplate> listIncludingTemplates(CedarResourceId id);

  List<FolderServerElement> listIncludingElements(CedarResourceId id);
}
