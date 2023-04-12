package org.metadatacenter.server.neo4j;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxies;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

public abstract class AbstractNeo4JUserSession {

  protected final Neo4JProxies proxies;
  protected final CedarUser cu;
  protected final CedarConfig cedarConfig;
  protected final LinkedDataUtil linkedDataUtil;
  protected final String globalRequestId;
  protected final String localRequestId;

  public AbstractNeo4JUserSession(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId, String localRequestId) {
    this.cedarConfig = cedarConfig;
    this.linkedDataUtil = cedarConfig.getLinkedDataUtil();
    this.proxies = proxies;
    this.cu = cu;
    this.globalRequestId = globalRequestId;
    this.localRequestId = localRequestId;
  }

  protected FolderServerUser getFilesystemResourceOwner(CedarFilesystemResourceId resourceId) {
    return proxies.filesystemResource().getFilesystemResourceOwner(resourceId);
  }

  public boolean userIsOwnerOfFilesystemResource(CedarFilesystemResourceId resourceId) {
    FolderServerUser owner = getFilesystemResourceOwner(resourceId);
    return owner != null && owner.getId().equals(cu.getId());
  }

  protected FolderServerUser getCategoryOwner(CedarCategoryId categoryId) {
    return proxies.category().getCategoryOwner(categoryId);
  }

  public boolean userHasPermission(CedarPermission permission) {
    return cu.has(permission);
  }

}
