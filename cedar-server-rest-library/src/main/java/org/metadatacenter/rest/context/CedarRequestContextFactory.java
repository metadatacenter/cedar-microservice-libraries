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

  public static CedarRequestContext fromAdminUser(CedarConfig cedarConfig, UserService userService) {
    try {
      String adminUserApiKey = cedarConfig.getAdminUserConfig().getApiKey();
      CedarUser adminUser = userService.findUserByApiKey(adminUserApiKey);
      return CedarRequestContextFactory.fromUser(adminUser);
    } catch (Exception ex) {
      log.error("Error while looking up admin user.", ex);
      return null;
    }
  }
}
