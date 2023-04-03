package org.metadatacenter.server.security.model.auth;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.CedarObjectConstants;

public enum CedarPermission {

  TEMPLATE_CREATE(CedarResourceType.Types.TEMPLATE, CedarObjectConstants.ACCESS_CREATE),
  TEMPLATE_READ(CedarResourceType.Types.TEMPLATE, CedarObjectConstants.ACCESS_READ),
  TEMPLATE_UPDATE(CedarResourceType.Types.TEMPLATE, CedarObjectConstants.ACCESS_UPDATE),
  TEMPLATE_DELETE(CedarResourceType.Types.TEMPLATE, CedarObjectConstants.ACCESS_DELETE),

  TEMPLATE_ELEMENT_CREATE(CedarResourceType.Types.ELEMENT, CedarObjectConstants.ACCESS_CREATE),
  TEMPLATE_ELEMENT_READ(CedarResourceType.Types.ELEMENT, CedarObjectConstants.ACCESS_READ),
  TEMPLATE_ELEMENT_UPDATE(CedarResourceType.Types.ELEMENT, CedarObjectConstants.ACCESS_UPDATE),
  TEMPLATE_ELEMENT_DELETE(CedarResourceType.Types.ELEMENT, CedarObjectConstants.ACCESS_DELETE),

  TEMPLATE_FIELD_CREATE(CedarResourceType.Types.FIELD, CedarObjectConstants.ACCESS_CREATE),
  TEMPLATE_FIELD_READ(CedarResourceType.Types.FIELD, CedarObjectConstants.ACCESS_READ),
  TEMPLATE_FIELD_UPDATE(CedarResourceType.Types.FIELD, CedarObjectConstants.ACCESS_UPDATE),
  TEMPLATE_FIELD_DELETE(CedarResourceType.Types.FIELD, CedarObjectConstants.ACCESS_DELETE),

  TEMPLATE_INSTANCE_CREATE(CedarResourceType.Types.INSTANCE, CedarObjectConstants.ACCESS_CREATE),
  TEMPLATE_INSTANCE_READ(CedarResourceType.Types.INSTANCE, CedarObjectConstants.ACCESS_READ),
  TEMPLATE_INSTANCE_UPDATE(CedarResourceType.Types.INSTANCE, CedarObjectConstants.ACCESS_UPDATE),
  TEMPLATE_INSTANCE_DELETE(CedarResourceType.Types.INSTANCE, CedarObjectConstants.ACCESS_DELETE),

  FOLDER_CREATE(CedarResourceType.Types.FOLDER, CedarObjectConstants.ACCESS_CREATE),
  FOLDER_READ(CedarResourceType.Types.FOLDER, CedarObjectConstants.ACCESS_READ),
  FOLDER_UPDATE(CedarResourceType.Types.FOLDER, CedarObjectConstants.ACCESS_UPDATE),
  FOLDER_DELETE(CedarResourceType.Types.FOLDER, CedarObjectConstants.ACCESS_DELETE),

  GROUP_CREATE(CedarResourceType.Types.GROUP, CedarObjectConstants.ACCESS_CREATE),
  GROUP_READ(CedarResourceType.Types.GROUP, CedarObjectConstants.ACCESS_READ),
  GROUP_UPDATE(CedarResourceType.Types.GROUP, CedarObjectConstants.ACCESS_UPDATE),
  GROUP_DELETE(CedarResourceType.Types.GROUP, CedarObjectConstants.ACCESS_DELETE),

  USER_READ(CedarResourceType.Types.USER, CedarObjectConstants.ACCESS_READ),
  USER_UPDATE(CedarResourceType.Types.USER, CedarObjectConstants.ACCESS_UPDATE),

  CATEGORY_CREATE(CedarResourceType.Types.CATEGORY, CedarObjectConstants.ACCESS_CREATE),
  CATEGORY_READ(CedarResourceType.Types.CATEGORY, CedarObjectConstants.ACCESS_READ),
  CATEGORY_UPDATE(CedarResourceType.Types.CATEGORY, CedarObjectConstants.ACCESS_UPDATE),
  CATEGORY_DELETE(CedarResourceType.Types.CATEGORY, CedarObjectConstants.ACCESS_DELETE),

  LOGGED_IN(CedarObjectConstants.LOGGED_IN, CedarObjectConstants.ACCESS_READ),
  SEARCH_INDEX_REINDEX(CedarObjectConstants.SEARCH_INDEX, CedarObjectConstants.ACCESS_CREATE),
  RULES_INDEX_REINDEX(CedarObjectConstants.RULES_INDEX, CedarObjectConstants.ACCESS_CREATE),

  UPDATE_PERMISSION_NOT_WRITABLE_NODE(CedarObjectConstants.NOT_WRITABLE_NODE_PERMISSIONS, CedarObjectConstants
      .ACCESS_UPDATE),
  READ_NOT_READABLE_NODE(CedarObjectConstants.NOT_READABLE_NODE, CedarObjectConstants.ACCESS_READ),
  WRITE_NOT_WRITABLE_NODE(CedarObjectConstants.NOT_WRITABLE_NODE, CedarObjectConstants.ACCESS_WRITE),
  WRITE_NOT_WRITABLE_CATEGORY(CedarObjectConstants.NOT_WRITABLE_CATEGORY, CedarObjectConstants.ACCESS_WRITE),
  UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY(CedarObjectConstants.NOT_WRITABLE_CATEGORY_PERMISSIONS,
      CedarObjectConstants.ACCESS_UPDATE),
  SEND_PROCESS_MESSAGE(CedarObjectConstants.PROCESS_MESSAGE, CedarObjectConstants.ACCESS_CREATE),
  UPDATE_NOT_ADMINISTERED_GROUP(CedarObjectConstants.NOT_ADMINISTERED_GROUP, CedarObjectConstants.ACCESS_UPDATE),

  MONITOR_READ(CedarObjectConstants.MONITOR, CedarObjectConstants.ACCESS_READ);

  private final String resourceType;
  private final String accessType;
  private final String permissionName;

  CedarPermission(String resourceType, String accessType) {
    this.resourceType = resourceType;
    this.accessType = accessType;
    this.permissionName = "permission_" + resourceType + "_" + accessType;
  }

  public String getAccessType() {
    return accessType;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getPermissionName() {
    return permissionName;
  }

  public static CedarPermission getUpdateForVersionedArtifactType(CedarResourceType artifacyType) {
    if (artifacyType.isVersioned()) {
      if (artifacyType == CedarResourceType.FIELD) {
        return TEMPLATE_FIELD_UPDATE;
      } else if (artifacyType == CedarResourceType.ELEMENT) {
        return TEMPLATE_ELEMENT_UPDATE;
      } else if (artifacyType == CedarResourceType.TEMPLATE) {
        return TEMPLATE_UPDATE;
      }
    }
    return null;
  }
}
