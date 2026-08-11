package org.metadatacenter.server.security;

import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserRole;

import java.util.*;

public abstract class CedarUserRolePermissionUtil {

  private static final Map<CedarUserRole, Set<String>> roleToPermissions;
  private static final Set<String> defaultUserPermissions;
  private static final Set<String> templateCreatorPermissions;
  private static final Set<String> metadataCreatorPermissions;
  private static final Set<String> userAdministratorPermissions;
  private static final Set<String> groupAdministratorPermissions;
  private static final Set<String> groupPrivilegedAdministratorPermissions;
  private static final Set<String> filesystemAdministratorPermissions;
  private static final Set<String> categoryAdministratorPermissions;
  private static final Set<String> artifactPrivilegedAdministratorPermissions;
  private static final Set<String> categoryPrivilegedAdministratorPermissions;
  private static final Set<String> searchReindexerPermissions;
  private static final Set<String> processMessageSenderPermission;
  private static final Set<String> monitorManagerPermission;

  static {
    defaultUserPermissions = new HashSet<>();
    defaultUserPermissions.add(CedarPermission.LOGGED_IN.getPermissionName());
    defaultUserPermissions.add(CedarPermission.CATEGORY_READ.getPermissionName());

    templateCreatorPermissions = new HashSet<>();
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_FIELD_CREATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_FIELD_READ.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_FIELD_UPDATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_FIELD_DELETE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_ELEMENT_CREATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_ELEMENT_READ.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_ELEMENT_UPDATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_ELEMENT_DELETE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_CREATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_READ.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_UPDATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.TEMPLATE_DELETE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.FOLDER_CREATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.FOLDER_READ.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.FOLDER_UPDATE.getPermissionName());
    templateCreatorPermissions.add(CedarPermission.FOLDER_DELETE.getPermissionName());

    metadataCreatorPermissions = new HashSet<>();
    metadataCreatorPermissions.add(CedarPermission.TEMPLATE_INSTANCE_CREATE.getPermissionName());
    metadataCreatorPermissions.add(CedarPermission.TEMPLATE_INSTANCE_READ.getPermissionName());
    metadataCreatorPermissions.add(CedarPermission.TEMPLATE_INSTANCE_UPDATE.getPermissionName());
    metadataCreatorPermissions.add(CedarPermission.TEMPLATE_INSTANCE_DELETE.getPermissionName());

    userAdministratorPermissions = new HashSet<>();
    userAdministratorPermissions.add(CedarPermission.USER_READ.getPermissionName());
    userAdministratorPermissions.add(CedarPermission.USER_UPDATE.getPermissionName());

    groupAdministratorPermissions = new HashSet<>();
    groupAdministratorPermissions.add(CedarPermission.GROUP_CREATE.getPermissionName());
    groupAdministratorPermissions.add(CedarPermission.GROUP_READ.getPermissionName());
    groupAdministratorPermissions.add(CedarPermission.GROUP_UPDATE.getPermissionName());
    groupAdministratorPermissions.add(CedarPermission.GROUP_DELETE.getPermissionName());

    // UPDATE_NOT_ADMINISTERED_GROUP is the override that lets a user change a group they do not
    // administer. It must not be in the default groupAdministrator role, which every user holds —
    // otherwise anyone can rename, re-staff or delete anyone's group. Kept in a separate privileged
    // role granted only to the built-in admin, mirroring categoryPrivilegedAdministrator.
    groupPrivilegedAdministratorPermissions = new HashSet<>();
    groupPrivilegedAdministratorPermissions.add(CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP.getPermissionName());

    filesystemAdministratorPermissions = new HashSet<>();
    filesystemAdministratorPermissions.add(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE.getPermissionName());
    filesystemAdministratorPermissions.add(CedarPermission.READ_NOT_READABLE_NODE.getPermissionName());
    filesystemAdministratorPermissions.add(CedarPermission.WRITE_NOT_WRITABLE_NODE.getPermissionName());

    categoryAdministratorPermissions = new HashSet<>();
    categoryAdministratorPermissions.add(CedarPermission.CATEGORY_CREATE.getPermissionName());
    categoryAdministratorPermissions.add(CedarPermission.CATEGORY_READ.getPermissionName());
    categoryAdministratorPermissions.add(CedarPermission.CATEGORY_UPDATE.getPermissionName());
    categoryAdministratorPermissions.add(CedarPermission.CATEGORY_DELETE.getPermissionName());

    // Held only by the built-in admin, like the other privileged roles: a verbatim write states its own
    // provenance, so it can attribute a change to another user.
    artifactPrivilegedAdministratorPermissions = new HashSet<>();
    artifactPrivilegedAdministratorPermissions.add(CedarPermission.WRITE_ARTIFACT_VERBATIM.getPermissionName());

    categoryPrivilegedAdministratorPermissions = new HashSet<>();
    categoryPrivilegedAdministratorPermissions.add(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY.getPermissionName());
    categoryPrivilegedAdministratorPermissions.add(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY.getPermissionName());

    searchReindexerPermissions = new HashSet<>();
    searchReindexerPermissions.add(CedarPermission.SEARCH_INDEX_REINDEX.getPermissionName());
    searchReindexerPermissions.add(CedarPermission.RULES_INDEX_REINDEX.getPermissionName());
    searchReindexerPermissions.add(CedarPermission.INCLUSION_SUBGRAPH_RECREATE.getPermissionName());

    processMessageSenderPermission = new HashSet<>();
    processMessageSenderPermission.add(CedarPermission.SEND_PROCESS_MESSAGE.getPermissionName());

    monitorManagerPermission = new HashSet<>();
    monitorManagerPermission.add(CedarPermission.MONITOR_READ.getPermissionName());

    roleToPermissions = new HashMap<>();
    roleToPermissions.put(CedarUserRole.DEFAULT_USER, defaultUserPermissions);
    roleToPermissions.put(CedarUserRole.TEMPLATE_CREATOR, templateCreatorPermissions);
    roleToPermissions.put(CedarUserRole.METADATA_CREATOR, metadataCreatorPermissions);
    roleToPermissions.put(CedarUserRole.USER_ADMINISTRATOR, userAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.GROUP_ADMINISTRATOR, groupAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.GROUP_PRIVILEGED_ADMINISTRATOR, groupPrivilegedAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.FILESYSTEM_ADMINISTRATOR, filesystemAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.CATEGORY_ADMINISTRATOR, categoryAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.CATEGORY_PRIVILEGED_ADMINISTRATOR, categoryPrivilegedAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.ARTIFACT_PRIVILEGED_ADMINISTRATOR, artifactPrivilegedAdministratorPermissions);
    roleToPermissions.put(CedarUserRole.SEARCH_REINDEXER, searchReindexerPermissions);
    roleToPermissions.put(CedarUserRole.PROCESS_MESSAGE_SENDER, processMessageSenderPermission);
    roleToPermissions.put(CedarUserRole.MONITOR_MANAGER, monitorManagerPermission);
  }

  public static void expandRolesIntoPermissions(CedarUser u) {
    Set<String> permissions = new HashSet<>();
    if (u.getRoles() != null) {
      for (CedarUserRole role : u.getRoles()) {
        if (role != null) {
          permissions.addAll(roleToPermissions.get(role));
        }
      }
    }
    List<String> permissionList = new ArrayList<>(permissions);
    Collections.sort(permissionList);
    // Assign through the setter: it also rebuilds the derived permission set that
    // CedarUser.has() consults, which mutating the list in place would leave stale
    u.setPermissions(permissionList);
  }
}
