package org.metadatacenter.util.test;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.user.UserServiceUtil;
import org.metadatacenter.util.json.JsonMapper;

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
  public BackendCallResult<CedarUser> updateUser(CedarUser user) {
    BackendCallResult<CedarUser> result = new BackendCallResult<>();
    users.put(user.getId(), user);
    result.setPayload(user);
    return result;
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
  public List<CedarUser> findAll() {
    return new ArrayList<>(users.values());
  }

}
