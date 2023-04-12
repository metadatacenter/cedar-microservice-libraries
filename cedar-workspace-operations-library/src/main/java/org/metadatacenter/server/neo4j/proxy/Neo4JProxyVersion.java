package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderVersion;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

public class Neo4JProxyVersion extends AbstractNeo4JProxy {

  Neo4JProxyVersion(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public FolderServerArtifact resourceWithPreviousVersion(CedarSchemaArtifactId resourceId) {
    String cypher = CypherQueryBuilderVersion.getResourceWithPreviousVersion();
    CypherParameters params = CypherParamBuilderArtifact.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerArtifact.class);
  }

}
