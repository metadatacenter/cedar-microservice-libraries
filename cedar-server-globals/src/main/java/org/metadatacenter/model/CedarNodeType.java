package org.metadatacenter.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CedarNodeType {

  FOLDER(Types.FOLDER, Prefix.FOLDERS, null),
  FIELD(Types.FIELD, Prefix.FIELDS, AtType.FIELD),
  ELEMENT(Types.ELEMENT, Prefix.ELEMENTS, AtType.ELEMENT),
  TEMPLATE(Types.TEMPLATE, Prefix.TEMPLATES, AtType.TEMPLATE),
  ELEMENT_INSTANCE(Types.ELEMENT_INSTANCE, Prefix.ELEMENT_INSTANCES, null),
  INSTANCE(Types.INSTANCE, Prefix.INSTANCES, null),
  USER(Types.USER, Prefix.USERS, null),
  GROUP(Types.GROUP, Prefix.GROUPS, null),
  MESSAGE(Types.MESSAGE, Prefix.MESSAGES, null),
  USERMESSAGE(Types.USERMESSAGE, Prefix.USERMESSAGES, null),
  PROCESS(Types.PROCESS_TYPE, Prefix.PROCESS_TYPES, null);

  public static class Types {
    public static final String FOLDER = "folder";
    public static final String FIELD = "field";
    public static final String ELEMENT = "element";
    public static final String TEMPLATE = "template";
    public static final String INSTANCE = "instance";
    public static final String ELEMENT_INSTANCE = "element-instance";
    public static final String USER = "user";
    public static final String GROUP = "group";
    public static final String MESSAGE = "message";
    public static final String USERMESSAGE = "user-message";
    public static final String PROCESS_TYPE = "process-type";
  }

  public static class Prefix {
    public static final String FOLDERS = "folders";
    public static final String FIELDS = "template-fields";
    public static final String ELEMENTS = "template-elements";
    public static final String TEMPLATES = "templates";
    public static final String ELEMENT_INSTANCES = "template-element-instances";
    public static final String INSTANCES = "template-instances";
    public static final String USERS = "users";
    public static final String GROUPS = "groups";
    public static final String MESSAGES = "messages";
    public static final String USERMESSAGES = "user-messages";
    public static final String PROCESS_TYPES = "process-types";
  }

  public static class AtType {
    public static final String AT_TYPE_PREFIX = "https://schema.metadatacenter.org/core/";
    public static final String FIELD = AT_TYPE_PREFIX + "TemplateField";
    public static final String ELEMENT = AT_TYPE_PREFIX + "TemplateElement";
    public static final String TEMPLATE = AT_TYPE_PREFIX + "Template";
  }

  private final String value;
  private final String prefix;
  private final String atType;

  CedarNodeType(String value, String prefix, String atType) {
    this.value = value;
    this.prefix = prefix;
    this.atType = atType;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getAtType() {
    return atType;
  }

  public static CedarNodeType forValue(String type) {
    for (CedarNodeType t : values()) {
      if (t.getValue().equals(type)) {
        return t;
      }
    }
    return null;
  }

  public static CedarNodeType forAtType(String atType) {
    if (atType != null) {
      for (CedarNodeType t : values()) {
        if (atType.equals(t.getAtType())) {
          return t;
        }
      }
    }
    return null;
  }

  public boolean isVersioned() {
    return this == ELEMENT || this == TEMPLATE || this == FIELD;
  }

  public FolderOrResource asFolderOrResource() {
    return this == FOLDER ? FolderOrResource.FOLDER : FolderOrResource.RESOURCE;
  }

}