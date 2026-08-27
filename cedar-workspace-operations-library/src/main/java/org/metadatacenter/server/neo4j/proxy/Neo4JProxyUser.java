package org.metadatacenter.server.neo4j.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
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
import org.metadatacenter.server.result.BackendCallError;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.user.UserServiceUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    // The update matches on id and returns nothing when no such node exists, which happens if the
    // user is deleted between the request's own lookup and this write. Reported rather than
    // dereferenced: buildUser() on the null threw, turning the race into a 500.
    if (updatedUser == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", user.getId());
      return result;
    }
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
      // As in updateUser: the node found at the top of this method can be gone by the time the write
      // runs, and the write then returns nothing.
      if (updatedUser == null) {
        result.addError(CedarErrorType.NOT_FOUND)
            .message("The user can not be found by id")
            .parameter("id", oldCedarUser.getId());
        return result;
      }
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

  /**
   * Adds an API key, refusing once the user holds {@code maxApiKeys}.
   */
  public BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys) {
    return changeApiKeys(userId, "adding an API key", keys -> {
      if (keys.size() >= maxApiKeys) {
        return refusal("You may have at most " + maxApiKeys + " API keys. Delete one before creating another.");
      }
      keys.add(newApiKey);
      return null;
    });
  }

  /**
   * Replaces one key's value, leaving its description, service name and enabled flag as they are. The
   * key count does not change, so this always applies.
   */
  public BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyId, String newKeyValue,
                                                       LocalDateTime newCreationDate) {
    return changeApiKeys(userId, "regenerating an API key", keys -> {
      CedarUserApiKey target = findById(keys, keyId);
      if (target == null) {
        return notFound();
      }
      target.setKey(newKeyValue);
      target.setCreationDate(newCreationDate);
      return null;
    });
  }

  /**
   * Removes an API key, refusing only when it is the user's last enabled one. A disabled key is not
   * the account's working key, so removing it cannot leave the account without one.
   */
  public BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyId) {
    return changeApiKeys(userId, "deleting an API key", keys -> {
      CedarUserApiKey target = findById(keys, keyId);
      if (target == null) {
        return notFound();
      }
      if (target.isEnabled()) {
        long remainingEnabled = keys.stream().filter(k -> k != target && k.isEnabled()).count();
        if (remainingEnabled < 1) {
          return refusal("You must keep at least one active API key. Regenerate this key instead of deleting it.");
        }
      }
      keys.remove(target);
      return null;
    });
  }

  /**
   * Applies a change to a user's API keys inside one write transaction, so the keys the change is
   * computed from are the keys it is written over, and writes back only the key properties.
   * <p>
   * Both halves matter. Reading the user at request time and writing it back later let a second
   * request land in between, which is how a revoked key returned and a freshly issued one was lost
   * despite the caller being handed it. Writing through the general user update on top of that
   * reverted every other field of the node to its state at that earlier read.
   */
  private BackendCallResult<CedarUser> changeApiKeys(CedarUserId userId, String eventDescription,
                                                     ApiKeyChange change) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();

    // Computed inside the transaction and translated afterwards: the driver may retry the work after
    // a transient failure, and errors recorded on the result would then accumulate across attempts.
    ApiKeyOutcome outcome = executeInWriteTransaction(tx -> {
      CypherQuery lockAndRead = new CypherQueryWithParameters(
          CypherQueryBuilderUser.touchAndReadUser(), CypherParamBuilderUser.touchUser(userId));
      FolderServerUser current = runInTransactionGetOne(tx, lockAndRead, FolderServerUser.class);
      if (current == null) {
        return ApiKeyOutcome.userNotFound();
      }

      List<CedarUserApiKey> keys = new ArrayList<>(current.buildUser().getApiKeys());
      ApiKeyOutcome refused = change.apply(keys);
      if (refused != null) {
        return refused;
      }

      CypherQuery write;
      try {
        write = new CypherQueryWithParameters(CypherQueryBuilderUser.updateUserApiKeys(),
            CypherParamBuilderUser.updateUserApiKeys(userId, keys));
      } catch (CedarProcessingException e) {
        return ApiKeyOutcome.serverError(e);
      }
      return ApiKeyOutcome.written(runInTransactionGetOne(tx, write, FolderServerUser.class));
    }, eventDescription);

    return outcome.into(result, userId);
  }

  private static CedarUserApiKey findById(List<CedarUserApiKey> keys, String keyId) {
    for (CedarUserApiKey k : keys) {
      if (k.getId() != null && k.getId().equals(keyId)) {
        return k;
      }
    }
    return null;
  }

  private static ApiKeyOutcome refusal(String message) {
    return ApiKeyOutcome.refused(CedarErrorType.INVALID_ARGUMENT, CedarErrorKey.INVALID_INPUT, message);
  }

  private static ApiKeyOutcome notFound() {
    // Keep the supplied identifier out of the stable client-facing message.
    return ApiKeyOutcome.refused(CedarErrorType.NOT_FOUND, CedarErrorKey.INVALID_INPUT, "API key not found.");
  }

  /** A change applied to the key list read inside the transaction; null means it was applied. */
  @FunctionalInterface
  private interface ApiKeyChange {
    ApiKeyOutcome apply(List<CedarUserApiKey> keys);
  }

  /** What the transaction produced: the written user, or the reason nothing was written. */
  private record ApiKeyOutcome(FolderServerUser written, CedarErrorType errorType, CedarErrorKey errorKey,
                               String message, Exception sourceException) {

    static ApiKeyOutcome written(FolderServerUser user) {
      return new ApiKeyOutcome(user, null, null, null, null);
    }

    static ApiKeyOutcome refused(CedarErrorType type, CedarErrorKey key, String message) {
      return new ApiKeyOutcome(null, type, key, message, null);
    }

    static ApiKeyOutcome userNotFound() {
      return refused(CedarErrorType.NOT_FOUND, CedarErrorKey.USER_NOT_FOUND, "The user can not be found by id");
    }

    static ApiKeyOutcome serverError(Exception e) {
      return new ApiKeyOutcome(null, CedarErrorType.SERVER_ERROR, null,
          "There was an error while updating the API keys", e);
    }

    BackendCallResult<CedarUser> into(BackendCallResult<CedarUser> result, CedarUserId userId) {
      if (errorType != null) {
        BackendCallError error = result.addError(errorType).message(message).parameter("id", userId.getId());
        if (errorKey != null) {
          error.errorKey(errorKey);
        }
        if (sourceException != null) {
          error.sourceException(sourceException);
        }
        return result;
      }
      if (written == null) {
        // The user vanished between the read and the write inside this transaction.
        return userNotFound().into(result, userId);
      }
      result.setPayload(written.buildUser());
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
