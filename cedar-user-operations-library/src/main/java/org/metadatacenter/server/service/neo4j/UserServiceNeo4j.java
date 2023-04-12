package org.metadatacenter.server.service.neo4j;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxyUser;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

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
  public BackendCallResult<CedarUser> updateUser(CedarUser user) {
    return userProxy.updateUser(user);
  }

  @Override
  public BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications) {
    return userProxy.patchUser(userId, modifications);
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
