package org.metadatacenter.server;

import org.metadatacenter.id.CedarResourceId;

import java.util.List;

public interface InclusionSubgraphServiceSession {


  boolean updateInclusionArcs(CedarResourceId sourceId, List<String> includedIds);
}
