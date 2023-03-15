package org.metadatacenter.model;

import com.fasterxml.jackson.annotation.JsonValue;
import org.metadatacenter.id.*;

public enum CedarResourceType {

  FOLDER(Types.FOLDER, Prefix.FOLDERS, null, CedarFolderId.class),
  FIELD(Types.FIELD, Prefix.FIELDS, AtType.FIELD, CedarFieldId.class),
  ELEMENT(Types.ELEMENT, Prefix.ELEMENTS, AtType.ELEMENT, CedarElementId.class),
  TEMPLATE(Types.TEMPLATE, Prefix.TEMPLATES, AtType.TEMPLATE, CedarTemplateId.class),
  //TODO: this should be removed once we don't need it in the frontend and the validation
  ELEMENT_INSTANCE(Types.ELEMENT_INSTANCE, Prefix.ELEMENT_INSTANCES, null, null),
  INSTANCE(Types.INSTANCE, Prefix.INSTANCES, null, CedarTemplateInstanceId.class),
  USER(Types.USER, Prefix.USERS, null, CedarUserId.class),
  GROUP(Types.GROUP, Prefix.GROUPS, null, CedarGroupId.class),
  CATEGORY(Types.CATEGORY, Prefix.CATEGORIES, null, CedarCategoryId.class),
  MESSAGE(Types.MESSAGE, Prefix.MESSAGES, null, null),
  USERMESSAGE(Types.USERMESSAGE, Prefix.USERMESSAGES, null, null),
  PROCESS(Types.PROCESS_TYPE, Prefix.PROCESS_TYPES, null, null);

  public static class Types {
    public static final String FOLDER = "folder";
    public static final String FIELD = "field";
    public static final String ELEMENT = "element";
    public static final String TEMPLATE = "template";
    public static final String INSTANCE = "instance";
    //TODO: this should be removed once we don't need it in the frontend and the validation
    public static final String ELEMENT_INSTANCE = "element-instance";
    public static final String USER = "user";
    public static final String GROUP = "group";
    public static final String MESSAGE = "message";
    public static final String USERMESSAGE = "user-message";
    public static final String PROCESS_TYPE = "process-type";
    public static final String CATEGORY = "category";
  }

  public static class Prefix {
    public static final String FOLDERS = "folders";
    public static final String FIELDS = "template-fields";
    public static final String ELEMENTS = "template-elements";
    public static final String TEMPLATES = "templates";
    //TODO: this should be removed once we don't need it in the frontend and the validation
    public static final String ELEMENT_INSTANCES = "template-element-instances";
    public static final String INSTANCES = "template-instances";
    public static final String USERS = "users";
    public static final String GROUPS = "groups";
    public static final String MESSAGES = "messages";
    public static final String USERMESSAGES = "user-messages";
    public static final String PROCESS_TYPES = "process-types";
    public static final String CATEGORIES = "categories";
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
  private final Class<CedarResourceId> resourceIdClass;

  CedarResourceType(String value, String prefix, String atType, Class resourceIdClass) {
    this.value = value;
    this.prefix = prefix;
    this.atType = atType;
    this.resourceIdClass = resourceIdClass;
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

  public Class<CedarResourceId> getResourceIdClass() {
    return resourceIdClass;
  }

  public static CedarResourceType forValue(String type) {
    for (CedarResourceType t : values()) {
      if (t.getValue().equals(type)) {
        return t;
      }
    }
    return null;
  }

  public static CedarResourceType forPrefix(String prefix) {
    for (CedarResourceType t : values()) {
      if (t.getPrefix().equals(prefix)) {
        return t;
      }
    }
    return null;
  }

  public static CedarResourceType forAtType(String atType) {
    if (atType != null) {
      for (CedarResourceType t : values()) {
        if (atType.equals(t.getAtType())) {
          return t;
        }
      }
    }
    return null;
  }

  public static <T extends CedarResourceId> CedarResourceType forResourceIdClass(Class<T> type) {
    if (type != null) {
      for (CedarResourceType t : values()) {
        if (type.equals(t.getResourceIdClass())) {
          return t;
        }
      }
    }
    return null;
  }


  public boolean isVersioned() {
    return this == ELEMENT || this == TEMPLATE || this == FIELD;
  }

}
