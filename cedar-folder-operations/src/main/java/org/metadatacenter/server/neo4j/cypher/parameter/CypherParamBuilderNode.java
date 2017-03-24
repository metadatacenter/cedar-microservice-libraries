package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.neo4j.PathUtil;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.util.ArrayList;
import java.util.List;

public class CypherParamBuilderNode extends AbstractCypherParamBuilder {

  public static CypherParameters matchNodeId(String nodeURL) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.NODE_ID, nodeURL);
    return params;
  }

  public static CypherParameters getNodeLookupByIDParameters(PathUtil pathUtil, String id) {
    return getNodeByIdentityAndName(id, pathUtil.getRootPath());
  }

  public static CypherParameters getAllNodesLookupParameters(int limit, int offset) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    return params;
  }

  public static CypherParameters getNodeByParentIdAndName(String parentId, String name) {
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, parentId);
    params.put(NodeProperty.NAME, name);
    return params;
  }

  public static CypherParameters getSharedWithMeLookupParameters(List<CedarNodeType> nodeTypes, int limit, int
      offset, String ownerId) {
    CypherParameters params = new CypherParameters();
    List<String> ntl = new ArrayList<>();
    nodeTypes.forEach(cnt -> ntl.add(cnt.getValue()));
    params.put(ParameterPlaceholder.NODE_TYPE_LIST, ntl);
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getSharedWithMeCountParameters(List<CedarNodeType> nodeTypes, String ownerId) {
    CypherParameters params = new CypherParameters();
    List<String> ntl = new ArrayList<>();
    nodeTypes.forEach(cnt -> ntl.add(cnt.getValue()));
    params.put(ParameterPlaceholder.NODE_TYPE_LIST, ntl);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getAllLookupParameters(List<CedarNodeType> nodeTypes, int limit, int
      offset, String ownerId, boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    List<String> ntl = new ArrayList<>();
    nodeTypes.forEach(cnt -> ntl.add(cnt.getValue()));
    params.put(ParameterPlaceholder.NODE_TYPE_LIST, ntl);
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters getAllCountParameters(List<CedarNodeType> nodeTypes, String ownerId,
                                                       boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    List<String> ntl = new ArrayList<>();
    nodeTypes.forEach(cnt -> ntl.add(cnt.getValue()));
    params.put(ParameterPlaceholder.NODE_TYPE_LIST, ntl);
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters getNodeById(String nodeURL) {
    return getNodeByIdentity(nodeURL);
  }

}