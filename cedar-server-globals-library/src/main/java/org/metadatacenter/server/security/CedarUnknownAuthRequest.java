package org.metadatacenter.server.security;

import org.metadatacenter.server.security.model.AuthRequest;

import javax.servlet.http.HttpServletRequest;

import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class CedarUnknownAuthRequest implements AuthRequest {

  private String authHeader;

  CedarUnknownAuthRequest(HttpServletRequest request) {
    if (request != null) {
      authHeader = request.getHeader(HTTP_HEADER_AUTHORIZATION);
    }
  }

  @Override
  public String getAuthString() {
    return null;
  }

  @Override
  public String getAuthHeader() {
    return authHeader;
  }
}
