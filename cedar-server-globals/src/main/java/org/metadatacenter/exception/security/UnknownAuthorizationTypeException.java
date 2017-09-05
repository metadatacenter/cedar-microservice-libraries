package org.metadatacenter.exception.security;

import org.metadatacenter.error.CedarErrorKey;

import javax.ws.rs.core.Response;

public class UnknownAuthorizationTypeException extends CedarAccessException {

  public UnknownAuthorizationTypeException(String authHeader) {
    super("Found unknown Authorization data: '" + authHeader + "'.",
        CedarErrorKey.AUTHORIZATION_TYPE_UNKNOWN, null, null);
    errorPack.parameter("authHeader", authHeader);
    errorPack.status(Response.Status.FORBIDDEN);
    if (authHeader == null || authHeader.isEmpty()) {
      showFullStackTrace = false;
    }
  }
}
