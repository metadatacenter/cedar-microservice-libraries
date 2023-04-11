package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.*;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderGroup;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderUser;
import org.metadatacenter.server.neo4j.cypher.query.AbstractCypherQueryBuilder;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategory;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderGroup;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;
import java.util.Map;

public class Neo4JProxyGroup extends AbstractNeo4JProxy {

  Neo4JProxyGroup(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  FolderServerGroup createGroup(CedarGroupId groupId, String name, String description, CedarUserId ownerId,
                                String specialGroup) {
    String cypher = CypherQueryBuilderGroup.createGroupWithAdministrator();
    CypherParameters params = CypherParamBuilderGroup.createGroup(groupId, name, description, ownerId, specialGroup);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerGroup.class);
  }

  List<FolderServerGroup> findGroups() {
    String cypher = CypherQueryBuilderGroup.findGroups();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  FolderServerGroup findGroupById(CedarGroupId groupId) {
    String cypher = CypherQueryBuilderGroup.getGroupById();
    CypherParameters params = CypherParamBuilderGroup.matchId(groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerGroup.class);
  }

  FolderServerGroup findGroupByName(String groupName) {
    String cypher = CypherQueryBuilderGroup.getGroupByName();
    CypherParameters params = CypherParamBuilderGroup.getGroupByName(groupName);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerGroup.class);
  }

  FolderServerGroup updateGroupById(CedarGroupId groupId, Map<NodeProperty, String> updateFields,
                                    CedarUserId updatedBy) {
    String cypher = CypherQueryBuilderGroup.updateGroupById(updateFields);
    CypherParameters params = CypherParamBuilderGroup.updateGroupById(groupId, updateFields, updatedBy);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerGroup.class);
  }

  boolean deleteGroupById(CedarGroupId groupId) {
    String cypher = CypherQueryBuilderGroup.deleteGroupById();
    CypherParameters params = CypherParamBuilderGroup.matchId(groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "deleting group");
  }

  List<FolderServerUser> findGroupMembers(CedarGroupId groupURL) {
    String cypher = CypherQueryBuilderGroup.getGroupUsersWithRelation(RelationLabel.MEMBEROF);
    CypherParameters params = CypherParamBuilderGroup.matchId(groupURL);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  List<FolderServerUser> findGroupAdministrators(CedarGroupId groupId) {
    String cypher = CypherQueryBuilderGroup.getGroupUsersWithRelation(RelationLabel.ADMINISTERS);
    CypherParameters params = CypherParamBuilderGroup.matchId(groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerUser.class);
  }

  void addUserGroupRelation(CedarUserId userId, CedarGroupId groupId, RelationLabel relation) {
    FolderServerGroup group = findGroupById(groupId);
    if (group != null) {
      FolderServerUser user = proxies.user().findUserById(userId);
      if (user != null) {
        addRelation(user, group, relation);
      }
    }
  }

  void removeUserGroupRelation(CedarUserId userId, CedarGroupId groupId, RelationLabel relation) {
    FolderServerGroup group = findGroupById(groupId);
    if (group != null) {
      FolderServerUser user = proxies.user().findUserById(userId);
      if (user != null) {
        removeRelation(user, group, relation);
      }
    }
  }

  FolderServerGroup findGroupBySpecialValue(String specialGroupName) {
    String cypher = CypherQueryBuilderGroup.getGroupBySpecialValue();
    CypherParameters params = CypherParamBuilderGroup.getGroupBySpecialValue(specialGroupName);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerGroup.class);
  }

  boolean addRelation(FolderServerUser user, FolderServerGroup group, RelationLabel relation) {
    String cypher = AbstractCypherQueryBuilder.addRelation(NodeLabel.USER, NodeLabel.GROUP, relation);
    CypherParameters params = AbstractCypherParamBuilder.matchFromNodeToNode(user.getId(), group.getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding relation");
  }

  boolean removeRelation(FolderServerUser user, FolderServerGroup group, RelationLabel relation) {
    String cypher = AbstractCypherQueryBuilder.removeRelation(NodeLabel.USER, NodeLabel.GROUP, relation);
    CypherParameters params = AbstractCypherParamBuilder.matchFromNodeToNode(user.getId(), group.getId());
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "removing relation");
  }

  public List<FolderServerGroup> findGroupsOfMemberUser(CedarUserId userId) {
    String cypher = CypherQueryBuilderGroup.getGroupsByMemberUserId();
    CypherParameters params = CypherParamBuilderUser.matchUserId(userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  public List<FolderServerGroup> findGroupsOfAdministratorUser(CedarUserId userId) {
    String cypher = CypherQueryBuilderGroup.getGroupsByAdministratorUserId();
    CypherParameters params = CypherParamBuilderUser.matchUserId(userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FolderServerGroup.class);
  }

  public FolderServerGroup getEverybodyGroup() {
    return findGroupBySpecialValue(Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
  }

  public long getGroupCount() {
    String cypher = CypherQueryBuilderGroup.getTotalCount();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

}
