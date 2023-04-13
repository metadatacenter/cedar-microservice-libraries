package org.metadatacenter.server.security;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;

public interface IAuthorizationResolver {

  CedarUser getUserAndEnsurePermission(LinkedDataUtil linkedDataUtil, AuthRequest request, CedarPermission
      permission, IUserService userService) throws CedarAccessException;

  CedarUser getUser(LinkedDataUtil linkedDataUtil, AuthRequest authRequest, IUserService userService) throws
      CedarAccessException;

}