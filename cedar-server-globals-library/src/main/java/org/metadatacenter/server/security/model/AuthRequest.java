package org.metadatacenter.server.security.model;

public interface AuthRequest {

  String getAuthString();

  String getAuthHeader();
}
