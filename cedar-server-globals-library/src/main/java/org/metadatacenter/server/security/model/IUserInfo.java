package org.metadatacenter.server.security.model;

public interface IUserInfo {

  String getSub();

  String getName();

  String getPreferredUsername();

  String getGivenName();

  String getFamilyName();

  String getEmail();
}
