package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;

public class Neo4JUserSessionUserService extends AbstractNeo4JUserSession implements UserServiceSession {

  private Neo4JUserSessionUserService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu,
                                      String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static UserServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser,
                                       String globalRequestId,
                                       String localRequestId) {
    return new Neo4JUserSessionUserService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public List<FolderServerUser> findUsers() {
    return proxies.user().findUsers();
  }

  @Override
  public FolderServerUser getUser(CedarUserId userId) {
    return proxies.user().findUserById(userId);
  }

  @Override
  public boolean addUserToEverybodyGroup(CedarUserId userId) throws CedarProcessingException {
    FolderServerGroup everybody = proxies.group().getEverybodyGroup();
    if (proxies.user().userExists(userId) && everybody != null) {
      return proxies.user().addUserToGroup(userId, everybody.getResourceId());
    }
    return false;
  }

  public long getUserCount() {
    return proxies.user().getUserCount();
  }
}
