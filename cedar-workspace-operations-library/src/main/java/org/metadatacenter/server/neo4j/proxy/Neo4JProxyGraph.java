package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.model.request.ResourceType;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.*;
import org.metadatacenter.server.neo4j.cypher.query.*;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;

import static org.metadatacenter.server.security.model.auth.CedarPermission.READ_NOT_READABLE_NODE;

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

  public boolean updateInclusionArcs(CedarResourceId sourceId, List<String> includedIds) {
    CypherParameters params = AbstractCypherParamBuilder.matchSourceAndTargetIds(sourceId, includedIds);
    CypherQuery delete = new CypherQueryWithParameters(
        CypherQueryBuilderGraph.updateInclusionArcsDelete(RelationLabel.INCLUDES), params);
    if (includedIds.isEmpty()) {
      return executeWriteBatch(List.of(delete), "updating inclusion arcs");
    }
    CypherQuery create = new CypherQueryWithParameters(
        CypherQueryBuilderGraph.updateInclusionArcsCreate(RelationLabel.INCLUDES), params);
    return executeWriteBatch(List.of(delete, create), "updating inclusion arcs");
  }

  public List<FolderServerTemplate> listIncludingTemplates(CedarResourceId sourceId, CedarUser cu) {
    boolean addPermissionConditions = !cu.has(READ_NOT_READABLE_NODE);
    String cypher = CypherQueryBuilderGraph.getIncludingTemplates(addPermissionConditions);
    CypherParameters params = CypherParamBuilderGraph.matchIdAndResourceTypeAndUser(sourceId, ResourceType.TEMPLATE,
        cu.getResourceId(), addPermissionConditions);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerTemplate.class);
  }

  public List<FolderServerElement> listIncludingElements(CedarResourceId sourceId, CedarUser cu) {
    boolean addPermissionConditions = !cu.has(READ_NOT_READABLE_NODE);
    String cypher = CypherQueryBuilderGraph.getIncludingElements(addPermissionConditions);
    CypherParameters params = CypherParamBuilderGraph.matchIdAndResourceTypeAndUser(sourceId, ResourceType.ELEMENT,
        cu.getResourceId(), addPermissionConditions);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerElement.class);
  }
}
