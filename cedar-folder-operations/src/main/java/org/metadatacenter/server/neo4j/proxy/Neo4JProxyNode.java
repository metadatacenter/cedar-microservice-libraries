package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.FolderOrResource;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.model.folderserver.FolderServerNode;
import org.metadatacenter.model.folderserver.FolderServerResource;
import org.metadatacenter.model.folderserver.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFolderContent;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderNode;
import org.metadatacenter.server.neo4j.cypher.parameter.*;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Neo4JProxyNode extends AbstractNeo4JProxy {

  Neo4JProxyNode(Neo4JProxies proxies) {
    super(proxies);
  }

  List<FolderServerNode> findNodePathById(String id) {
    List<FolderServerNode> pathList = new ArrayList<>();
    String cypher = CypherQueryBuilderNode.getNodeLookupQueryById();
    CypherParameters params = CypherParamBuilderNode.getNodeLookupByIDParameters(proxies.pathUtil, id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode pathListJsonNode = jsonNode.at("/results/0/data/0/row/0");
    if (pathListJsonNode != null && !pathListJsonNode.isMissingNode()) {
      pathListJsonNode.forEach(f -> {
        // relationships are also included, filter them out
        Map pathElement = buildMap(f);
        if (pathElement != null && !pathElement.isEmpty()) {
          FolderServerNode cf = buildNode(f);
          if (cf != null) {
            pathList.add(cf);
          }
        }
      });
    }
    return pathList;
  }

  long findFolderContentsFilteredCount(String folderId, List<CedarNodeType> nodeTypeList) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsFilteredCountQuery();
    CypherParameters params = CypherParamBuilderFolder.getFolderContentsFilteredCountParameters(folderId, nodeTypeList);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return count(jsonNode);
  }

  long findFolderContentsCount(String folderId) {
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsCountQuery();
    CypherParameters params = CypherParamBuilderFolder.getFolderContentsCountParameters(folderId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return count(jsonNode);
  }

  List<FolderServerNode> findAllNodes(int limit, int offset, List<String> sortList) {
    String cypher = CypherQueryBuilderNode.getAllNodesLookupQuery(sortList);
    CypherParameters params = CypherParamBuilderNode.getAllNodesLookupParameters(limit, offset);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  long findAllNodesCount() {
    String cypher = CypherQueryBuilderNode.getAllNodesCountQuery();
    CypherQuery q = new CypherQueryLiteral(cypher);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return count(jsonNode);
  }

  List<FolderServerNode> findFolderContents(String folderId, Collection<CedarNodeType> nodeTypes, int
      limit, int offset, List<String> sortList, CedarUser cu) {
    boolean addPermissionConditions = true;
    String cypher = CypherQueryBuilderFolderContent.getFolderContentsLookupQuery(sortList, addPermissionConditions);
    CypherParameters params = CypherParamBuilderFolderContent.getFolderContentsLookupParameters(folderId, nodeTypes,
        limit, offset, cu.getId(), addPermissionConditions);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  FolderServerNode findNodeByParentIdAndName(String parentId, String name) {
    String cypher = CypherQueryBuilderNode.getNodeByParentIdAndName();
    CypherParameters params = CypherParamBuilderNode.getNodeByParentIdAndName(parentId, name);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode node = jsonNode.at("/results/0/data/0/row/0");
    return buildNode(node);
  }

  void updateNodeOwner(String nodeURL, String userURL, FolderOrResource folderOrResource) {
    FolderServerUser user = proxies.user().findUserById(userURL);
    if (user != null) {
      if (folderOrResource == FolderOrResource.FOLDER) {
        FolderServerFolder folder = proxies.folder().findFolderById(nodeURL);
        if (folder != null) {
          proxies.folder().updateOwner(folder, user);
        }
      } else {
        FolderServerResource resource = proxies.resource().findResourceById(nodeURL);
        if (resource != null) {
          proxies.resource().updateOwner(resource, user);
        }
      }
    }
  }

  FolderServerUser getNodeOwner(String nodeURL) {
    String cypher = CypherQueryBuilderNode.getNodeOwner();
    CypherParameters params = CypherParamBuilderNode.matchNodeId(nodeURL);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    JsonNode userNode = jsonNode.at("/results/0/data/0/row/0");
    return buildUser(userNode);
  }

  public List<FolderServerNode> viewSharedWithMeFiltered(List<CedarNodeType> nodeTypes, int limit, int offset,
                                                         List<String> sortList, CedarUser cu) {
    String cypher = CypherQueryBuilderNode.getSharedWithMeLookupQuery(sortList);
    CypherParameters params = CypherParamBuilderNode.getSharedWithMeLookupParameters(nodeTypes, limit, offset, cu
        .getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  public long viewSharedWithMeFilteredCount(List<CedarNodeType> nodeTypes, CedarUser cu) {
    String cypher = CypherQueryBuilderNode.getSharedWithMeCountQuery();
    CypherParameters params = CypherParamBuilderNode.getSharedWithMeCountParameters(nodeTypes, cu.getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return count(jsonNode);
  }

  public List<FolderServerNode> viewAllFiltered(List<CedarNodeType> nodeTypes, int limit, int offset, List<String>
      sortList, CedarUser cu) {
    boolean addPermissionConditions = true;
    String cypher = CypherQueryBuilderNode.getAllLookupQuery(sortList, addPermissionConditions);
    CypherParameters params = CypherParamBuilderNode.getAllLookupParameters(nodeTypes, limit, offset, cu.getId(),
        addPermissionConditions);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  public long viewAllFilteredCount(List<CedarNodeType> nodeTypes, CedarUser cu) {
    boolean addPermissionConditions = true;
    String cypher = CypherQueryBuilderNode.getAllCountQuery(addPermissionConditions);
    CypherParameters params = CypherParamBuilderNode.getAllCountParameters(nodeTypes, cu.getId(),
        addPermissionConditions);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return count(jsonNode);
  }

  public List<FolderServerNode> findAllDescendantNodesById(String id) {
    String cypher = CypherQueryBuilderNode.getAllDescendantNodes();
    CypherParameters params = CypherParamBuilderNode.getNodeById(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  public List<FolderServerNode> findAllNodesVisibleByUserId(String id) {
    String cypher = CypherQueryBuilderNode.getAllVisibleByUserQuery();
    CypherParameters params = CypherParamBuilderUser.matchUserId(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }

  public List<FolderServerNode> findAllNodesVisibleByGroupId(String id) {
    String cypher = CypherQueryBuilderNode.getAllVisibleByGroupQuery();
    CypherParameters params = CypherParamBuilderGroup.matchGroupId(id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    JsonNode jsonNode = executeCypherQueryAndCommit(q);
    return listNodes(jsonNode);
  }
}
