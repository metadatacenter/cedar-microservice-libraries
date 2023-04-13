package org.metadatacenter.server.logging.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AppLogParam {

  CLASS_NAME("className"),
  METHOD_NAME("methodName"),
  LINE_NUMBER("lineNumber"),
  GLOBAL_REQUEST_ID_SOURCE("globalRequestIdSource"),
  QUERY_PARAMETERS("queryParameters"),
  HTTP_METHOD("httpMethod"),
  USER_ID("userId"),
  CLIENT_SESSION_ID("clientSessionId"),
  JWT_TOKEN_HASH("jwtTokenHash"),
  AUTH_SOURCE("authSource"),
  PATH("path"),
  ORIGINAL_QUERY("originalQuery"),
  RUNNABLE_QUERY("runnableQuery"),
  INTERPOLATED_QUERY("interpolatedQuery"),
  RUNNABLE_QUERY_HASH("runnableQueryHash"),
  QUERY_PARAMETERS_HASH("queryParametersHash"),
  EXCEPTION("exception"),
  OPERATION("operation");

  private final String value;

  AppLogParam(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }
}
