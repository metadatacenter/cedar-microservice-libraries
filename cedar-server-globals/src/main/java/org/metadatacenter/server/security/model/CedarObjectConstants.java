package org.metadatacenter.server.security.model;


public final class CedarObjectConstants {


  public static final String SEARCH_INDEX = "search_index";
  public static final String RULES_INDEX = "rules_index";
  public static final String LOGGED_IN = "logged_in";
  public static final String NOT_READABLE_NODE = "not_readable_node";
  public static final String NOT_WRITABLE_NODE = "not_writable_node";
  public static final String NOT_WRITABLE_NODE_PERMISSIONS = "not_writable_node_permissions";
  public static final String NOT_WRITABLE_CATEGORY = "not_writable_category";
  public static final String NOT_WRITABLE_CATEGORY_PERMISSIONS = "not_writable_category_permissions";
  public static final String PROCESS_MESSAGE = "process_message";
  public static final String NOT_ADMINISTERED_GROUP = "not_administered_group";
  public static final String MONITOR = "monitor";

  public static final String ACCESS_CREATE = "create";
  public static final String ACCESS_READ = "read";
  public static final String ACCESS_UPDATE = "update";
  public static final String ACCESS_DELETE = "delete";
  // This does not align with the other ACCESS
  public static final String ACCESS_WRITE = "write";

  private CedarObjectConstants() {
  }
}
