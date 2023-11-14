package org.metadatacenter.server.neo4j;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.metadatacenter.model.CedarResourceType;

public enum NodeLabel {

  RESOURCE(ComposedLabel.RESOURCE, SimpleLabel.RESOURCE),
  USER(ComposedLabel.USER, SimpleLabel.USER),
  GROUP(ComposedLabel.GROUP, SimpleLabel.GROUP),
  CATEGORY(ComposedLabel.CATEGORY, SimpleLabel.CATEGORY),
  FILESYSTEM_RESOURCE(SimpleLabel.FILESYSTEM_RESOURCE, SimpleLabel.FILESYSTEM_RESOURCE),
  FOLDER(ComposedLabel.FOLDER, SimpleLabel.FOLDER),
  SYSTEM_FOLDER(ComposedLabel.SYSTEM_FOLDER, SimpleLabel.SYSTEM_FOLDER),
  USER_HOME_FOLDER(ComposedLabel.USER_HOME_FOLDER, SimpleLabel.USER_HOME_FOLDER),
  ARTIFACT(ComposedLabel.ARTIFACT, SimpleLabel.ARTIFACT),
  FIELD(ComposedLabel.FIELD, SimpleLabel.FIELD),
  ELEMENT(ComposedLabel.ELEMENT, SimpleLabel.ELEMENT),
  TEMPLATE(ComposedLabel.TEMPLATE, SimpleLabel.TEMPLATE),
  INSTANCE(ComposedLabel.INSTANCE, SimpleLabel.INSTANCE);

  private static final String S = ":";

  public static class SimpleLabel {
    public static final String RESOURCE = "Resource";
    public static final String USER = "User";
    public static final String GROUP = "Group";
    public static final String CATEGORY = "Category";
    public static final String FILESYSTEM_RESOURCE = "FileSystemResource";
    public static final String FOLDER = "Folder";
    public static final String SYSTEM_FOLDER = "SystemFolder";
    public static final String USER_HOME_FOLDER = "UserHomeFolder";
    public static final String ARTIFACT = "Artifact";
    public static final String FIELD = "Field";
    public static final String ELEMENT = "Element";
    public static final String TEMPLATE = "Template";
    public static final String INSTANCE = "Instance";
  }

  public static class ComposedLabel {
    public static final String RESOURCE = SimpleLabel.RESOURCE;
    public static final String USER = SimpleLabel.USER + S + RESOURCE;
    public static final String GROUP = SimpleLabel.GROUP + S + RESOURCE;
    public static final String CATEGORY = SimpleLabel.CATEGORY + S + RESOURCE;
    public static final String FILESYSTEM_RESOURCE = SimpleLabel.FILESYSTEM_RESOURCE + S + RESOURCE;
    public static final String FOLDER = SimpleLabel.FOLDER + S + FILESYSTEM_RESOURCE;
    public static final String SYSTEM_FOLDER = SimpleLabel.SYSTEM_FOLDER + S + FOLDER;
    public static final String USER_HOME_FOLDER = SimpleLabel.USER_HOME_FOLDER + S + FOLDER;
    public static final String ARTIFACT = SimpleLabel.ARTIFACT + S + FILESYSTEM_RESOURCE;
    public static final String FIELD = SimpleLabel.FIELD + S + ARTIFACT;
    public static final String ELEMENT = SimpleLabel.ELEMENT + S + ARTIFACT;
    public static final String TEMPLATE = SimpleLabel.TEMPLATE + S + ARTIFACT;
    public static final String INSTANCE = SimpleLabel.INSTANCE + S + ARTIFACT;
  }

  private final String composedLabel;
  private final String simpleLabel;

  NodeLabel(String composedLabel, String simpleLabel) {
    this.composedLabel = composedLabel;
    this.simpleLabel = simpleLabel;
  }

  public String getComposedLabel() {
    return composedLabel;
  }

  public String getSimpleLabel() {
    return simpleLabel;
  }

  public static NodeLabel forComposedLabel(String composedLabel) {
    for (NodeLabel t : values()) {
      if (t.getComposedLabel().equals(composedLabel)) {
        return t;
      }
    }
    return null;
  }

  public static NodeLabel forSimpleLabel(String simpleLabel) {
    for (NodeLabel t : values()) {
      if (t.getSimpleLabel().equals(simpleLabel)) {
        return t;
      }
    }
    return null;
  }

  public static NodeLabel forCedarResourceType(CedarResourceType resourceType) {
    return switch (resourceType) {
      case FIELD -> FIELD;
      case ELEMENT -> ELEMENT;
      case TEMPLATE -> TEMPLATE;
      case INSTANCE -> INSTANCE;
      default -> null;
    };
  }

  @JsonIgnore
  public boolean isFolder() {
    return this == FOLDER || this == SYSTEM_FOLDER || this == USER_HOME_FOLDER;
  }

  @Override
  public String toString() {
    return composedLabel;
  }
}
