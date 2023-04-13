package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.AbstractCypherParamBuilder;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderUser;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderUser;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.user.UserServiceUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.util.List;
import java.util.Map;

public class Neo4JProxyUser extends AbstractNeo4JProxy {

  Neo4JProxyUser(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public FolderServerUser createUser(CedarUser user) {
    String cypher = CypherQueryBuilderUser.createUser();
    CypherParameters params = null;
    try {
      params = CypherParamBuilderUser.createUser(user, cedarConfig);
    } catch (CedarProcessingException e) {
      log.error("Error while assembling create user parameters", e);
    }
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWriteGetOne(q, FolderServerUser.class);
  }

  boolean addUserToGroup(CedarUserId userId, CedarGroupId groupId) {
    String cypher = CypherQueryBuilderUser.addUserToGroup();
    CypherParameters params = AbstractCypherParamBuilder.matchUserAndGroup(userId, groupId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeWrite(q, "adding user to group");
  }

  public List<FolderServerUser> findUsers() {
    String cypher = CypherQueryBuilderUser.findUsers();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeReadGetList(q, FolderServerUser.class);
  }

  public FolderServerUser findUserById(CedarUserId userId) {
    String cypher = CypherQueryBuilderUser.getUserById();
    CypherParameters params = CypherParamBuilderUser.getUserById(userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerUser.class);
  }

  public BackendCallResult<CedarUser> updateUser(CedarUser user) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    String cypher = CypherQueryBuilderUser.updateUser();
    CypherParameters params;
    try {
      params = CypherParamBuilderUser.updateUser(user, cedarConfig);
    } catch (CedarProcessingException e) {
      result.addError(CedarErrorType.SERVER_ERROR)
          .sourceException(e)
          .message("There was an error while updating the user")
          .parameter("id", user.getId());
      return result;
    }
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    FolderServerUser updatedUser = executeWriteGetOne(q, FolderServerUser.class);
    result.setPayload(updatedUser.buildUser());
    return result;
  }

  public FolderServerUser findUserByApiKey(String apiKey) {
    String cypher = CypherQueryBuilderUser.getUserByApiKey();
    CypherParameters params = CypherParamBuilderUser.getUserByApiKey(apiKey);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerUser.class);
  }

  public BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    FolderServerUser oldUser = findUserById(userId);
    if (oldUser == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }

    CedarUser oldCedarUser = oldUser.buildUser();

    Map<String, Object> modificationsMap = JsonMapper.MAPPER.convertValue(modifications, Map.class);
    CedarUser modifiedCedarUser = UserServiceUtil.validateModifications(oldCedarUser, modificationsMap);

    if (modifiedCedarUser != null) {
      String cypher = CypherQueryBuilderUser.updateUser();
      CypherParameters params;
      try {
        params = CypherParamBuilderUser.updateUser(modifiedCedarUser, cedarConfig);
      } catch (CedarProcessingException e) {
        result.addError(CedarErrorType.SERVER_ERROR)
            .sourceException(e)
            .message("There was an error while updating the user")
            .parameter("id", oldCedarUser.getId());
        return result;
      }
      CypherQuery q = new CypherQueryWithParameters(cypher, params);
      FolderServerUser updatedUser = executeWriteGetOne(q, FolderServerUser.class);
      result.setPayload(updatedUser.buildUser());
      return result;
    } else {
      result.addError(CedarErrorType.INVALID_ARGUMENT)
          .message("The requested modifications are invalid")
          .parameter("id", userId.getId())
          .parameter("modifications", modifications);
      return result;
    }
  }

  public boolean userExists(CedarUserId userId) {
    String cypher = CypherQueryBuilderUser.userExists();
    CypherParameters params = CypherParamBuilderUser.getUserById(userId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetBoolean(q);
  }

  public long getUserCount() {
    String cypher = CypherQueryBuilderUser.getTotalCount();
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }
}

