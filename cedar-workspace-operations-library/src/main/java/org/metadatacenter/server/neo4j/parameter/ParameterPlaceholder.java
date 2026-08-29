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
  USER_ID_LIST("userIdList"),
  READ_USER_ID_LIST("readUserIdList"),
  ATTACH_USER_ID_LIST("attachUserIdList"),
  WRITE_USER_ID_LIST("writeUserIdList"),
  CHANGE_OWNER_USER_ID_LIST("changeOwnerUserIdList"),
  CHANGE_PERMISSIONS_USER_ID_LIST("changePermissionsUserIdList"),
  PUBLISH_USER_ID_LIST("publishUserIdList"),
  CREATE_DRAFT_USER_ID_LIST("createDraftUserIdList"),
  GROUP_ID_LIST("groupIdList"),
  READ_GROUP_ID_LIST("readGroupIdList"),
  ATTACH_GROUP_ID_LIST("attachGroupIdList"),
  WRITE_GROUP_ID_LIST("writeGroupIdList"),
  CHANGE_OWNER_GROUP_ID_LIST("changeOwnerGroupIdList"),
  CHANGE_PERMISSIONS_GROUP_ID_LIST("changePermissionsGroupIdList"),
  PUBLISH_GROUP_ID_LIST("publishGroupIdList"),
  CREATE_DRAFT_GROUP_ID_LIST("createDraftGroupIdList"),
  OWNER_ID("ownerId"),
  ADMINISTRATOR_ID_LIST("administratorIdList"),
  MEMBER_ID_LIST("memberIdList"),
  CURRENT_REVISION("currentRevision"),
  PARENT_ID("parentId"),
  USER_ID("userId"),
  FROM_ID("fromId"),
  TO_ID("toId"),
  RESOURCE_TYPE_LIST("resourceTypeList"),
  RESOURCE_TYPE("resourceType"),
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
