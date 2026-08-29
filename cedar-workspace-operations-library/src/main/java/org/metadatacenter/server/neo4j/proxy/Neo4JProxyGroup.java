package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.*;
import org.metadatacenter.server.RevisionConflictException;
import org.metadatacenter.server.RevisionPrecondition;
import org.metadatacenter.server.VersionedGroupUsers;
import org.metadatacenter.server.VersionedResource;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderGroup;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderUser;
import org.metadatacenter.server.neo4j.cypher.query.AbstractCypherQueryBuilder;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderGroup;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.security.model.auth.CedarGroupUser;
import org.metadatacenter.server.security.model.auth.CedarGroupUsers;
import org.metadatacenter.util.json.JsonMapper;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.types.Node;

import java.util.ArrayList;
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

  VersionedResource<FolderServerGroup> findVersionedGroupById(CedarGroupId groupId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderGroup.getVersionedGroupById(), CypherParamBuilderGroup.matchId(groupId));
    return executeInReadTransaction(tx -> readVersionedGroup(run(tx, query)), "reading a versioned group");
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

  VersionedResource<FolderServerGroup> updateGroupById(CedarGroupId groupId,
                                                        Map<NodeProperty, String> updateFields,
                                                        CedarUserId updatedBy,
                                                        RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      CypherParameters params = CypherParamBuilderGroup.updateGroupById(groupId, updateFields, updatedBy);
      Result locked = run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderGroup.lockGroupRevision(), CypherParamBuilderGroup.matchId(groupId)));
      if (!locked.hasNext()) {
        return null;
      }
      long currentRevision = locked.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      return readVersionedGroup(run(tx, new CypherQueryWithParameters(
          CypherQueryBuilderGroup.updateGroupById(updateFields), params)));
    }, "updating a versioned group");
  }

  boolean deleteGroupById(CedarGroupId groupId) {
    return deleteGroupById(groupId, RevisionPrecondition.any());
  }

  boolean deleteGroupById(CedarGroupId groupId, RevisionPrecondition precondition) {
    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderGroup.lockGroupRevision(), CypherParamBuilderGroup.matchId(groupId));
      Result locked = run(tx, lock);
      if (!locked.hasNext()) {
        return false;
      }
      long currentRevision = locked.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }
      CypherQueryWithParameters delete = new CypherQueryWithParameters(
          CypherQueryBuilderGroup.deleteGroupById(), CypherParamBuilderGroup.matchId(groupId));
      return run(tx, delete).hasNext();
    }, "deleting a versioned group");
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

  VersionedGroupUsers findVersionedGroupUsers(CedarGroupId groupId) {
    CypherQueryWithParameters query = new CypherQueryWithParameters(
        CypherQueryBuilderGroup.getVersionedGroupUsers(), CypherParamBuilderGroup.matchId(groupId));
    return executeInReadTransaction(tx -> readVersionedGroupUsers(run(tx, query)),
        "reading versioned group users");
  }

  /**
   * Locks the membership aggregate, checks the caller's validator, and replaces both relation
   * families in the same transaction. The returned representation is read from the result rows of
   * that replacement, so its body and revision cannot be taken from different commits.
   */
  VersionedGroupUsers replaceGroupUsers(CedarGroupId groupId, CedarGroupUsers requested,
                                        RevisionPrecondition precondition) {
    List<String> userIds = new ArrayList<>();
    List<String> administratorIds = new ArrayList<>();
    List<String> memberIds = new ArrayList<>();
    for (CedarGroupUser user : requested.getUsers()) {
      String userId = user.getUser().getId();
      userIds.add(userId);
      if (user.isAdministrator()) {
        administratorIds.add(userId);
      }
      if (user.isMember()) {
        memberIds.add(userId);
      }
    }

    return executeInWriteTransaction(tx -> {
      CypherQueryWithParameters lock = new CypherQueryWithParameters(
          CypherQueryBuilderGroup.lockGroupMembership(), CypherParamBuilderGroup.matchId(groupId));
      Result lockResult = run(tx, lock);
      if (!lockResult.hasNext()) {
        return null;
      }
      long currentRevision = lockResult.next().get("revision").asLong();
      if (!precondition.matches(currentRevision)) {
        throw new RevisionConflictException(currentRevision);
      }

      CypherQueryWithParameters replace = new CypherQueryWithParameters(
          CypherQueryBuilderGroup.replaceGroupUsers(),
          CypherParamBuilderGroup.replaceGroupUsers(groupId, userIds, administratorIds, memberIds, currentRevision));
      return readVersionedGroupUsers(run(tx, replace));
    }, "replacing versioned group users");
  }

  private Result run(Transaction tx, CypherQueryWithParameters query) {
    return tx.run(query.getRunnableQuery(), query.getParameterMap());
  }

  private VersionedResource<FolderServerGroup> readVersionedGroup(Result result) {
    if (!result.hasNext()) {
      return null;
    }
    Record record = result.next();
    Node node = record.get("resource").asNode();
    JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
    return new VersionedResource<>(buildClass(json, FolderServerGroup.class), record.get("revision").asLong());
  }

  private VersionedGroupUsers readVersionedGroupUsers(Result result) {
    CedarGroupUsers groupUsers = new CedarGroupUsers();
    long revision = -1;
    while (result.hasNext()) {
      Record record = result.next();
      revision = record.get("revision").asLong();
      if (!record.get("user").isNull()) {
        Node node = record.get("user").asNode();
        JsonNode json = JsonMapper.MAPPER.valueToTree(node.asMap());
        FolderServerUser user = buildClass(json, FolderServerUser.class);
        groupUsers.addUser(new CedarGroupUser(user.buildExtract(),
            record.get("administrator").asBoolean(), record.get("member").asBoolean()));
      }
    }
    return revision < 0 ? null : new VersionedGroupUsers(groupUsers, revision);
  }

  CypherQuery addUserGroupRelationQuery(CedarUserId userId, CedarGroupId groupId, RelationLabel relation) {
    String cypher = AbstractCypherQueryBuilder.addRelation(NodeLabel.USER, NodeLabel.GROUP, relation);
    CypherParameters params = AbstractCypherParamBuilder.matchFromNodeToNode(userId.getId(), groupId.getId());
    return new CypherQueryWithParameters(cypher, params);
  }

  CypherQuery removeUserGroupRelationQuery(CedarUserId userId, CedarGroupId groupId, RelationLabel relation) {
    String cypher = AbstractCypherQueryBuilder.removeRelation(NodeLabel.USER, NodeLabel.GROUP, relation);
    CypherParameters params = AbstractCypherParamBuilder.matchFromNodeToNode(userId.getId(), groupId.getId());
    return new CypherQueryWithParameters(cypher, params);
  }

  FolderServerGroup findGroupBySpecialValue(String specialGroupName) {
    String cypher = CypherQueryBuilderGroup.getGroupBySpecialValue();
    CypherParameters params = CypherParamBuilderGroup.getGroupBySpecialValue(specialGroupName);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerGroup.class);
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
