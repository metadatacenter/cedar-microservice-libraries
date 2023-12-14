package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;

public class Neo4JUserSessionInclusionSubgraphService extends AbstractNeo4JUserSession implements InclusionSubgraphServiceSession {

  private Neo4JUserSessionInclusionSubgraphService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static InclusionSubgraphServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId, String localRequestId) {
    return new Neo4JUserSessionInclusionSubgraphService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public boolean updateInclusionArcs(CedarResourceId sourceId, List<String> includedIds) {
    if (includedIds.isEmpty()) {
      return proxies.graph().updateInclusionArcsDelete(sourceId, includedIds);
    } else {
      proxies.graph().updateInclusionArcsDelete(sourceId, includedIds);
      return proxies.graph().updateInclusionArcsCreate(sourceId, includedIds);
    }
  }

  @Override
  public List<FolderServerTemplate> listIncludingTemplates(CedarResourceId id) {
    return proxies.graph().listIncludingTemplates(id);
  }

  @Override
  public List<FolderServerElement> listIncludingElements(CedarResourceId id) {
    return proxies.graph().listIncludingElements(id);
  }
}
