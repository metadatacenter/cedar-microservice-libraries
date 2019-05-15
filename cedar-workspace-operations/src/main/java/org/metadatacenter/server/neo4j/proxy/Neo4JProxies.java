package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.PathUtil;

public class Neo4JProxies {

  protected final CedarConfig cedarConfig;
  protected final Neo4jConfig config;
  protected final PathUtil pathUtil;
  protected final LinkedDataUtil linkedDataUtil;

  private final Neo4JProxyAdmin adminProxy;
  private final Neo4JProxyFolder folderProxy;
  private final Neo4JProxyGroup groupProxy;
  private final Neo4JProxyUser userProxy;
  private final Neo4JProxyPermission permissionProxy;
  private final Neo4JProxyArtifact artifactProxy;
  private final Neo4JProxyResource resourceProxy;
  private final Neo4JProxyGraph graphProxy;
  private final Neo4JProxyVersion versionProxy;

  public Neo4JProxies(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    this.config = Neo4jConfig.fromCedarConfig(cedarConfig);
    this.linkedDataUtil = cedarConfig.getLinkedDataUtil();
    this.pathUtil = new Neo4JPathUtil(cedarConfig);

    this.adminProxy = new Neo4JProxyAdmin(this, cedarConfig);
    this.folderProxy = new Neo4JProxyFolder(this, cedarConfig);
    this.groupProxy = new Neo4JProxyGroup(this, cedarConfig);
    this.userProxy = new Neo4JProxyUser(this, cedarConfig);
    this.permissionProxy = new Neo4JProxyPermission(this, cedarConfig);
    this.artifactProxy = new Neo4JProxyArtifact(this, cedarConfig);
    this.resourceProxy = new Neo4JProxyResource(this, cedarConfig);
    this.graphProxy = new Neo4JProxyGraph(this, cedarConfig);
    this.versionProxy = new Neo4JProxyVersion(this, cedarConfig);
  }

  public Neo4JProxyAdmin admin() {
    return adminProxy;
  }

  public Neo4JProxyFolder folder() {
    return folderProxy;
  }

  public Neo4JProxyGroup group() {
    return groupProxy;
  }

  public Neo4JProxyUser user() {
    return userProxy;
  }

  public Neo4JProxyPermission permission() {
    return permissionProxy;
  }

  public Neo4JProxyArtifact artifact() {
    return artifactProxy;
  }

  public Neo4JProxyResource resource() {
    return resourceProxy;
  }

  public Neo4JProxyGraph graph() {
    return graphProxy;
  }

  public Neo4JProxyVersion version() {
    return versionProxy;
  }

  public LinkedDataUtil getLinkedDataUtil() {
    return linkedDataUtil;
  }
}
