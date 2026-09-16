package org.metadatacenter.server.service.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxyUser;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.service.ApiKeyLookupCache;
import org.metadatacenter.server.service.UserService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserServiceNeo4j implements UserService {

  private Neo4JProxyUser userProxy;
  private final ApiKeyLookupCache apiKeyCache;

  public UserServiceNeo4j(Neo4JProxyUser userProxy) {
    this(userProxy, new ApiKeyLookupCache());
  }

  UserServiceNeo4j(Neo4JProxyUser userProxy, ApiKeyLookupCache apiKeyCache) {
    this.userProxy = userProxy;
    this.apiKeyCache = apiKeyCache;
  }

  @Override
  public CedarUser createUser(CedarUser user) {
    FolderServerUser u = userProxy.createUser(user);
    return u == null ? null : u.buildUser();
  }

  @Override
  public CedarUser findUser(CedarUserId userId) {
    FolderServerUser user = userProxy.findUserById(userId);
    return user == null ? null : user.buildUser();
  }

  /**
   * Resolves an API key to the user it authenticates, or null where it authenticates nobody.
   *
   * <p>The graph matches the secret against every key a user holds, disabled ones included, so the
   * user record decides. Disabling a key is how access is withdrawn from a credential that is kept,
   * and the rest of the service already reads it that way: deleting a key refuses only when it is the
   * last enabled one, because a disabled key is not the account's working key. A lookup that ignored
   * the flag made the withdrawal cosmetic. The disabled key still read the profile it belongs to,
   * secrets and all, and still issued new keys of its own.
   */
  @Override
  public CedarUser findUserByApiKey(String apiKey) {
    // The graph record is what is cached, not the CedarUser built from it: buildUser() makes a new
    // instance per call, and the caller mutates what it is given (the resolver stamps the auth
    // source on it). Handing out one shared object would let one request's edit reach another's.
    ApiKeyLookupCache.Lookup cached = apiKeyCache.get(apiKey);
    FolderServerUser user;
    if (cached.isPresent()) {
      user = cached.getUser();
    } else {
      user = userProxy.findUserByApiKey(apiKey);
      apiKeyCache.put(apiKey, user);
    }
    if (user == null) {
      return null;
    }
    CedarUser cedarUser = user.buildUser();
    // Still evaluated per call rather than remembered as a verdict, so the reasoning above about
    // disabled keys stays in one place and reads the same whether the record came from the graph
    // or from the cache.
    boolean enabled = cedarUser.getApiKeys().stream()
        .anyMatch(key -> key.isEnabled() && apiKey.equals(key.getKey()));
    return enabled ? cedarUser : null;
  }

  @Override
  public BackendCallResult<CedarUser> setHomeFolderId(CedarUserId userId, String homeFolderId) {
    return invalidatingCache(userProxy.setHomeFolderId(userId, homeFolderId));
  }

  @Override
  public BackendCallResult<CedarUser> replaceRolesAndPermissions(CedarUserId userId, List<CedarUserRole> roles,
                                                                 List<String> permissions) {
    return invalidatingCache(userProxy.replaceRolesAndPermissions(userId, roles, permissions));
  }

  @Override
  public BackendCallResult<CedarUser> replaceUiPreferences(CedarUserId userId,
                                                            CedarUserUIPreferences uiPreferences) {
    return invalidatingCache(userProxy.replaceUiPreferences(userId, uiPreferences));
  }

  @Override
  public BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    return invalidatingCache(userProxy.patchUser(userId, modifications));
  }

  @Override
  public BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys) {
    return invalidatingCache(userProxy.addApiKey(userId, newApiKey, maxApiKeys));
  }

  @Override
  public BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyId, String newKeyValue,
                                                       OffsetDateTime newCreationDate) {
    return invalidatingCache(userProxy.regenerateApiKey(userId, keyId, newKeyValue, newCreationDate));
  }

  @Override
  public BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyId) {
    return invalidatingCache(userProxy.deleteApiKey(userId, keyId));
  }

  /**
   * Clears the API key cache after a write, on the way back out.
   * <p>
   * Every mutation here can change what a key resolves to or what the resolved user carries - a key
   * added, regenerated or deleted, and roles, permissions, home folder or preferences that travel on
   * the cached record. Clearing on all of them rather than reasoning about which ones matter keeps
   * this from rotting the first time a field moves.
   * <p>
   * <strong>Only this JVM's cache.</strong> A key revoked through the user server is refused there
   * immediately and by every other service once its entry expires. See {@link ApiKeyLookupCache}.
   */
  private BackendCallResult<CedarUser> invalidatingCache(BackendCallResult<CedarUser> result) {
    apiKeyCache.invalidateAll();
    return result;
  }

  @Override
  public List<CedarUser> findAll() {
    List<FolderServerUser> users = userProxy.findUsers();
    List<CedarUser> ret = new ArrayList<>();
    for (FolderServerUser fsu : users) {
      if (fsu != null) {
        ret.add(fsu.buildUser());
      }
    }
    return ret;
  }

}
