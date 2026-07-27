package org.metadatacenter.rest.context;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CedarRequestContextFactory {

  private static LinkedDataUtil linkedDataUtil;

  private static final Logger log = LoggerFactory.getLogger(CedarRequestContextFactory.class);

  public static void init(LinkedDataUtil linkedDataUtil) {
    CedarRequestContextFactory.linkedDataUtil = linkedDataUtil;
  }

  public static CedarRequestContext fromUser(CedarUser user) throws CedarAccessException {
    LocalRequestContext lrc = new LocalRequestContext(linkedDataUtil, user);
    if (lrc.getUserCreationException() != null) {
      throw lrc.getUserCreationException();
    }
    return lrc;
  }

  /**
   * Builds a request context for the admin user. This never returns null: when the admin user
   * can not be resolved (user store unreachable, admin not provisioned), the caller gets an
   * unchecked exception naming the actual cause, instead of a null context that surfaces later
   * as a NullPointerException far from the problem.
   */
  public static CedarRequestContext fromAdminUser(CedarConfig cedarConfig, UserService userService) {
    String adminUserApiKey = cedarConfig.getAdminUserConfig().getApiKey();
    CedarUser adminUser;
    try {
      adminUser = userService.findUserByApiKey(adminUserApiKey);
    } catch (Exception ex) {
      log.error("The admin user could not be looked up by its API key.", ex);
      throw new IllegalStateException(
          "The admin user could not be looked up by its API key. Check that the user store (Neo4j) is reachable.", ex);
    }
    if (adminUser == null) {
      log.error("The admin user was not found by its API key.");
      throw new IllegalStateException(
          "The admin user was not found by its API key. Check that the admin user is provisioned in the user store.");
    }
    try {
      return CedarRequestContextFactory.fromUser(adminUser);
    } catch (CedarAccessException ex) {
      log.error("The admin request context could not be created.", ex);
      throw new IllegalStateException("The admin request context could not be created.", ex);
    }
  }
}
