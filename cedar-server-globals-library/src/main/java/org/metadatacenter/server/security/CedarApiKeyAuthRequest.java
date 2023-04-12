package org.metadatacenter.server.security;

import org.metadatacenter.server.security.model.AuthRequest;

import javax.servlet.http.HttpServletRequest;

import static org.metadatacenter.constant.HttpConstants.HTTP_AUTH_HEADER_APIKEY_PREFIX;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class CedarApiKeyAuthRequest implements AuthRequest {

  private String authHeader;
  private String apiKey;

  private CedarApiKeyAuthRequest() {
  }

  CedarApiKeyAuthRequest(HttpServletRequest request) {
    if (request != null) {
      authHeader = request.getHeader(HTTP_HEADER_AUTHORIZATION);
      if (authHeader != null) {
        if (authHeader.regionMatches(true, 0, HTTP_AUTH_HEADER_APIKEY_PREFIX, 0, HTTP_AUTH_HEADER_APIKEY_PREFIX.length())) {
          apiKey = authHeader.substring(HTTP_AUTH_HEADER_APIKEY_PREFIX.length());
        }
      }
    }
  }

  public CedarApiKeyAuthRequest(String apiKey) {
    this.apiKey = apiKey;
    this.authHeader = HTTP_AUTH_HEADER_APIKEY_PREFIX + apiKey;
  }

  @Override
  public String getAuthString() {
    return apiKey;
  }

  @Override
  public String getAuthHeader() {
    return authHeader;
  }
}
