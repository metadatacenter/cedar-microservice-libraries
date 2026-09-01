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
import org.metadatacenter.server.service.UserService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserServiceNeo4j implements UserService {

  private Neo4JProxyUser userProxy;

  public UserServiceNeo4j(Neo4JProxyUser userProxy) {
    this.userProxy = userProxy;
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

  @Override
  public CedarUser findUserByApiKey(String apiKey) {
    FolderServerUser user = userProxy.findUserByApiKey(apiKey);
    return user == null ? null : user.buildUser();
  }

  @Override
  public BackendCallResult<CedarUser> setHomeFolderId(CedarUserId userId, String homeFolderId) {
    return userProxy.setHomeFolderId(userId, homeFolderId);
  }

  @Override
  public BackendCallResult<CedarUser> replaceRolesAndPermissions(CedarUserId userId, List<CedarUserRole> roles,
                                                                 List<String> permissions) {
    return userProxy.replaceRolesAndPermissions(userId, roles, permissions);
  }

  @Override
  public BackendCallResult<CedarUser> replaceUiPreferences(CedarUserId userId,
                                                            CedarUserUIPreferences uiPreferences) {
    return userProxy.replaceUiPreferences(userId, uiPreferences);
  }

  @Override
  public BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    return userProxy.patchUser(userId, modifications);
  }

  @Override
  public BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys) {
    return userProxy.addApiKey(userId, newApiKey, maxApiKeys);
  }

  @Override
  public BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyId, String newKeyValue,
                                                       LocalDateTime newCreationDate) {
    return userProxy.regenerateApiKey(userId, keyId, newKeyValue, newCreationDate);
  }

  @Override
  public BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyId) {
    return userProxy.deleteApiKey(userId, keyId);
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
