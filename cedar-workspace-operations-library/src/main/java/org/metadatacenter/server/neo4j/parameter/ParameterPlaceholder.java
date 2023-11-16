package org.metadatacenter.server.neo4j.parameter;

import org.metadatacenter.server.neo4j.cypher.CypherQueryParameter;

public enum ParameterPlaceholder implements CypherQueryParameter {

  ID("_id"),
  FOLDER_ID("folderId"),
  PARENT_FOLDER_ID("parentFolderId"),
  RESOURCE_ID("resourceId"),
  ARTIFACT_ID("artifactId"),
  FS_RESOURCE_ID("fsResourceId"),
  GROUP_ID("groupId"),
  PARENT_ID("parentId"),
  USER_ID("userId"),
  FROM_ID("fromId"),
  TO_ID("toId"),
  RESOURCE_TYPE_LIST("resourceTypeList"),
  LIMIT("limit"),
  OFFSET("offset"),
  SOURCE_ID("sourceId"),
  TARGET_ID("targetId"),
  TARGET_IDS("targetIds"),
  IS_BASED_ON("isBasedOn"),
  EVERYBODY_PERMISSION("everybodyPermission"),
  CATEGORY_ID("categoryId"),
  SPECIAL_GROUP("specialGroup"),
  NAME("name"),
  PREVIOUS_VERSION("previousVersion"),
  PUBLICATION_STATUS("publicationStatus"),
  PARENT_CATEGORY_ID("parentCategoryId"),
  IDENTIFIER("identifier"),
  API_KEY("apiKey");

  private final String value;

  ParameterPlaceholder(String value) {
    this.value = value;
  }

  @Override
  public String getValue() {
    return value;
  }

  public static ParameterPlaceholder forValue(String type) {
    for (ParameterPlaceholder t : values()) {
      if (t.getValue().equals(type)) {
        return t;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return value;
  }
}
