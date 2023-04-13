package org.metadatacenter.util.test;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.security.CedarApiKeyAuthRequest;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

public class TestUserUtil {

  private static String getTestUserAuthHeader(CedarUserId userId) {
    CedarUser user = CedarDataServices.getNeoUserService().findUser(userId);
    AuthRequest authRequest = new CedarApiKeyAuthRequest(user.getFirstActiveApiKey());
    return authRequest.getAuthHeader();
  }

  public static String getTestUser1AuthHeader(CedarConfig cedarConfig) {
    String uuid = cedarConfig.getTestUsers().getTestUser1().getId();
    return getTestUserAuthHeader(CedarUserId.build(uuid));
  }

  public static String getTestUser2AuthHeader(CedarConfig cedarConfig) {
    String uuid = cedarConfig.getTestUsers().getTestUser2().getId();
    return getTestUserAuthHeader(CedarUserId.build(uuid));
  }

  public static String getAdminUserAuthHeader(CedarConfig cedarConfig) {
    AuthRequest authRequest = new CedarApiKeyAuthRequest(cedarConfig.getAdminUserConfig().getApiKey());
    return authRequest.getAuthHeader();
  }
}
