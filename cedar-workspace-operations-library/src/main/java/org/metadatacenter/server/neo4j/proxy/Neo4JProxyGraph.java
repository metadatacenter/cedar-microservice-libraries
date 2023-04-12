package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.*;
import org.metadatacenter.server.neo4j.cypher.query.*;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;

public class Neo4JProxyGraph extends AbstractNeo4JProxy {

  Neo4JProxyGraph(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public List<FolderServerArc> getOutgoingArcs(CedarResourceId resourceId) {
    String cypher = CypherQueryBuilderGraph.getOutgoingArcs();
    CypherParameters params = CypherParamBuilderResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetArcList(q);
  }

  public List<FolderServerArc> getIncomingArcs(CedarResourceId resourceId) {
    String cypher = CypherQueryBuilderGraph.getIncomingArcs();
    CypherParameters params = CypherParamBuilderResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetArcList(q);
  }

  public FolderServerUser createUser(JsonNode node) {
    String cypher = CypherQueryBuilderUser.createUser();
    CypherParameters params = CypherParamBuilderUser.mapAllProperties(node);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerUser.class);
  }

  public FolderServerGroup createGroup(JsonNode node) {
    String cypher = CypherQueryBuilderGroup.createGroup();
    CypherParameters params = CypherParamBuilderUser.mapAllProperties(node);
    CypherParamBuilderGroup.tweakGroupProperties(node, params);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerGroup.class);
  }

  public FileSystemResource createFilesystemResource(JsonNode node) {
    FileSystemResource folderServerNode = buildNode(node);
    CedarResourceType type = folderServerNode.getType();
    String cypher = null;

    if (type == CedarResourceType.FOLDER) {
      FolderServerFolder fsFolder = buildFolder(node);
      cypher = CypherQueryBuilderFolder.createFolderWithoutParent(fsFolder);
    } else {
      FolderServerArtifact fsResource = buildResource(node);
      cypher = CypherQueryBuilderArtifact.createResourceWithoutParent(fsResource);
    }
    CypherParameters params = CypherParamBuilderGraph.mapAllProperties(node);
    CypherParamBuilderGraph.tweakNodeProperties(node, params);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FileSystemResource.class);
  }

  public boolean createArc(CedarResourceId sourceArtifactId, RelationLabel relationLabel, CedarResourceId targetArtifactId) {
    String cypher = CypherQueryBuilderGraph.createArc(relationLabel);
    CypherParameters params = AbstractCypherParamBuilder.matchSourceAndTarget(sourceArtifactId, targetArtifactId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "creating arc");
  }
}
