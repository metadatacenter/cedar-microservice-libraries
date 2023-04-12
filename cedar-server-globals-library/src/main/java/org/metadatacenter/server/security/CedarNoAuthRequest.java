package org.metadatacenter.server.security;

import org.metadatacenter.server.security.model.AuthRequest;

public class CedarNoAuthRequest implements AuthRequest {

  CedarNoAuthRequest() {
  }

  @Override
  public String getAuthString() {
    return null;
  }

  @Override
  public String getAuthHeader() {
    return null;
  }
}
