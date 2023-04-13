package org.metadatacenter.server.security;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

public final class Authorization {

  private static IAuthorizationResolver resolver;
  private static IUserService userService;

  private Authorization() {
  }

  public static void setAuthorizationResolver(IAuthorizationResolver r) {
    resolver = r;
  }

  public static void setUserService(IUserService us) {
    userService = us;
  }

  public static CedarUser getUserAndEnsurePermission(LinkedDataUtil linkedDataUtil, AuthRequest authRequest,
                                                     CedarPermission permission) throws CedarAccessException {
    return resolver.getUserAndEnsurePermission(linkedDataUtil, authRequest, permission, userService);
  }

  public static CedarUser getUser(LinkedDataUtil linkedDataUtil, AuthRequest authRequest) throws CedarAccessException {
    return resolver.getUser(linkedDataUtil, authRequest, userService);
  }

}