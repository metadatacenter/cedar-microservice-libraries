package org.metadatacenter.util.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.user.UserServiceUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user service backed by an in-memory store, replacing the Neo4j-backed one during integration
 * tests. Installed via Authorization.setUserService after the Dropwizard test application has
 * started, it lets API-key authentication work with no live Neo4j; Keycloak is not involved
 * either, since the API-key path of AuthorizationKeycloakAndApiKeyResolver never contacts it.
 *
 * The class implements the full UserService, so it can also stand in where a server uses the
 * user service beyond authentication (for example UsersResource.injectUserService). Patch
 * semantics reuse UserServiceUtil.validateModifications, the same logic the Neo4j proxy applies,
 * so behavior differs only in storage. Like the stored service, it keeps its own copy of each user
 * and hands out a new copy on every read, so a change counts only when it is written through the
 * service.
 */
public class InMemoryUserService implements UserService {

  private final Map<String, CedarUser> users = new LinkedHashMap<>();

  public InMemoryUserService(CedarUser... users) {
    replaceAll(users);
  }

  /**
   * Replaces every stored user with copies of the given ones. The service stays the same object, so
   * an application that was handed it keeps using it.
   */
  synchronized void replaceAll(CedarUser... replacements) {
    users.clear();
    for (CedarUser user : replacements) {
      users.put(user.getId(), copyOf(user));
    }
  }

  /**
   * A copy that shares nothing with the original. The stored service builds a new user on every read,
   * because its callers edit what they are given; storing or handing out the caller's own object
   * would count an edit that no write ever made as saved. Absent fields stay absent rather than
   * becoming nulls the setters refuse.
   */
  static CedarUser copyOf(CedarUser user) {
    if (user == null) {
      return null;
    }
    ObjectNode json = JsonMapper.STRICT_MAPPER.valueToTree(user);
    Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
    while (fields.hasNext()) {
      if (fields.next().getValue().isNull()) {
        fields.remove();
      }
    }
    try {
      CedarUser copy = JsonMapper.STRICT_MAPPER.treeToValue(json, CedarUser.class);
      copy.setAuthSource(user.getAuthSource());
      return copy;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not copy the user " + user.getId(), e);
    }
  }

  @Override
  public synchronized CedarUser createUser(CedarUser user) {
    users.put(user.getId(), copyOf(user));
    return copyOf(user);
  }

  @Override
  public synchronized CedarUser findUser(CedarUserId userId) {
    if (userId == null) {
      return null;
    }
    return copyOf(users.get(userId.getId()));
  }

  @Override
  public synchronized CedarUser findUserByApiKey(String apiKey) {
    if (apiKey == null) {
      return null;
    }
    for (CedarUser user : users.values()) {
      for (CedarUserApiKey key : user.getApiKeys()) {
        if (key.isEnabled() && apiKey.equals(key.getKey())) {
          return copyOf(user);
        }
      }
    }
    return null;
  }

  @Override
  public BackendCallResult<CedarUser> setHomeFolderId(CedarUserId userId, String homeFolderId) {
    return changeUser(userId, user -> user.setHomeFolderId(homeFolderId));
  }

  @Override
  public BackendCallResult<CedarUser> replaceRolesAndPermissions(CedarUserId userId, List<CedarUserRole> roles,
                                                                 List<String> permissions) {
    return changeUser(userId, user -> {
      user.setRoles(new ArrayList<>(roles));
      user.setPermissions(new ArrayList<>(permissions));
    });
  }

  @Override
  public BackendCallResult<CedarUser> replaceUiPreferences(CedarUserId userId,
                                                            CedarUserUIPreferences uiPreferences) {
    return changeUser(userId, user -> user.setUiPreferences(uiPreferences));
  }

  @Override
  public synchronized BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    CedarUser oldUser = users.get(userId.getId());
    if (oldUser == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }
    Map<String, Object> modificationsMap = JsonMapper.STRICT_MAPPER.convertValue(modifications, Map.class);
    CedarUser modifiedUser = UserServiceUtil.validateModifications(oldUser, modificationsMap);
    if (modifiedUser != null) {
      users.put(modifiedUser.getId(), copyOf(modifiedUser));
      result.setPayload(copyOf(modifiedUser));
    } else {
      result.addError(CedarErrorType.INVALID_ARGUMENT)
          .message("The requested modifications are invalid")
          .parameter("id", userId.getId())
          .parameter("modifications", modifications);
    }
    return result;
  }

  @Override
  public BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys) {
    return changeApiKeys(userId, keys -> {
      if (keys.size() >= maxApiKeys) {
        return "You may have at most " + maxApiKeys + " API keys. Delete one before creating another.";
      }
      keys.add(newApiKey);
      return null;
    });
  }

  @Override
  public BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyId, String newKeyValue,
                                                       OffsetDateTime newCreationDate) {
    return changeApiKeys(userId, keys -> {
      CedarUserApiKey target = findById(keys, keyId);
      if (target == null) {
        return API_KEY_NOT_FOUND;
      }
      target.setKey(newKeyValue);
      target.setCreationDate(newCreationDate);
      return null;
    });
  }

  @Override
  public BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyId) {
    return changeApiKeys(userId, keys -> {
      CedarUserApiKey target = findById(keys, keyId);
      if (target == null) {
        return API_KEY_NOT_FOUND;
      }
      if (target.isEnabled()) {
        long remainingEnabled = keys.stream().filter(k -> k != target && k.isEnabled()).count();
        if (remainingEnabled < 1) {
          return "You must keep at least one active API key. Regenerate this key instead of deleting it.";
        }
      }
      keys.remove(target);
      return null;
    });
  }

  /**
   * The map stands in for the graph, so the change is applied to the stored user rather than to a
   * copy the caller holds — which is the property the Neo4j implementation gets from doing the read
   * and the write in one transaction.
   */
  private synchronized BackendCallResult<CedarUser> changeApiKeys(CedarUserId userId, ApiKeyChange change) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    CedarUser user = users.get(userId.getId());
    if (user == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }
    List<CedarUserApiKey> keys = new ArrayList<>(user.getApiKeys() == null ? List.of() : user.getApiKeys());
    String refusal = change.apply(keys);
    if (refusal != null) {
      result.addError(API_KEY_NOT_FOUND.equals(refusal) ? CedarErrorType.NOT_FOUND : CedarErrorType.INVALID_ARGUMENT)
          .message(refusal)
          .parameter("id", userId.getId());
      return result;
    }
    user.setApiKeys(keys);
    result.setPayload(copyOf(user));
    return result;
  }

  private synchronized BackendCallResult<CedarUser> changeUser(CedarUserId userId, UserChange change) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    CedarUser user = users.get(userId.getId());
    if (user == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }
    change.apply(user);
    result.setPayload(copyOf(user));
    return result;
  }

  private static CedarUserApiKey findById(List<CedarUserApiKey> keys, String keyId) {
    for (CedarUserApiKey k : keys) {
      if (k.getId() != null && k.getId().equals(keyId)) {
        return k;
      }
    }
    return null;
  }

  private static final String API_KEY_NOT_FOUND = "API key not found.";

  /** Applies the change to the stored key list, or returns the message explaining the refusal. */
  @FunctionalInterface
  private interface ApiKeyChange {
    String apply(List<CedarUserApiKey> keys);
  }

  @FunctionalInterface
  private interface UserChange {
    void apply(CedarUser user);
  }

  @Override
  public synchronized List<CedarUser> findAll() {
    return users.values().stream().map(InMemoryUserService::copyOf).toList();
  }

}
