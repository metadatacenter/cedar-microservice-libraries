package org.metadatacenter.server.neo4j;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.basic.FolderServerNode;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxies;
import org.metadatacenter.server.security.model.user.CedarUser;

public abstract class AbstractNeo4JUserSession {

  protected final Neo4JProxies proxies;
  protected final CedarUser cu;
  protected final CedarConfig cedarConfig;
  protected final LinkedDataUtil linkedDataUtil;
  protected final String requestId;

  public AbstractNeo4JUserSession(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String requestId) {
    this.cedarConfig = cedarConfig;
    this.linkedDataUtil = cedarConfig.getLinkedDataUtil();
    this.proxies = proxies;
    this.cu = cu;
    this.requestId = requestId;
  }

  protected FolderServerUser getNodeOwner(String nodeURL) {
    return proxies.node().getNodeOwner(nodeURL);
  }

  public boolean userIsOwnerOfNode(FolderServerNode node) {
    return userIsOwnerOfNode(node.getId());
  }

  public boolean userIsOwnerOfNode(String nodeURL) {
    FolderServerUser owner = getNodeOwner(nodeURL);
    return owner != null && owner.getId().equals(cu.getId());
  }

}