package org.metadatacenter.util.test;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * so behavior differs only in storage.
 */
public class InMemoryUserService implements UserService {

  private final Map<String, CedarUser> users = new LinkedHashMap<>();

  public InMemoryUserService(CedarUser... users) {
    for (CedarUser user : users) {
      this.users.put(user.getId(), user);
    }
  }

  @Override
  public CedarUser createUser(CedarUser user) {
    users.put(user.getId(), user);
    return user;
  }

  @Override
  public CedarUser findUser(CedarUserId userId) {
    if (userId == null) {
      return null;
    }
    return users.get(userId.getId());
  }

  @Override
  public CedarUser findUserByApiKey(String apiKey) {
    if (apiKey == null) {
      return null;
    }
    for (CedarUser user : users.values()) {
      for (CedarUserApiKey key : user.getApiKeys()) {
        if (key.isEnabled() && apiKey.equals(key.getKey())) {
          return user;
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
  public BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    CedarUser oldUser = users.get(userId.getId());
    if (oldUser == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }
    Map<String, Object> modificationsMap = JsonMapper.MAPPER.convertValue(modifications, Map.class);
    CedarUser modifiedUser = UserServiceUtil.validateModifications(oldUser, modificationsMap);
    if (modifiedUser != null) {
      users.put(modifiedUser.getId(), modifiedUser);
      result.setPayload(modifiedUser);
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
                                                       LocalDateTime newCreationDate) {
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
  private BackendCallResult<CedarUser> changeApiKeys(CedarUserId userId, ApiKeyChange change) {
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
    result.setPayload(user);
    return result;
  }

  private BackendCallResult<CedarUser> changeUser(CedarUserId userId, UserChange change) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    CedarUser user = users.get(userId.getId());
    if (user == null) {
      result.addError(CedarErrorType.NOT_FOUND)
          .message("The user can not be found by id")
          .parameter("id", userId.getId());
      return result;
    }
    change.apply(user);
    result.setPayload(user);
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
  public List<CedarUser> findAll() {
    return new ArrayList<>(users.values());
  }

}
