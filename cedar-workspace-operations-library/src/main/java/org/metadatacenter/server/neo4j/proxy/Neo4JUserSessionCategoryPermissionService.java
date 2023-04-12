package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.category.*;
import org.metadatacenter.server.security.model.user.CedarGroupExtract;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Neo4JUserSessionCategoryPermissionService extends AbstractNeo4JUserSession implements CategoryPermissionServiceSession {

  private Neo4JUserSessionCategoryPermissionService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId,
                                                    String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static CategoryPermissionServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId,
                                                     String localRequestId) {
    return new Neo4JUserSessionCategoryPermissionService(cedarConfig, proxies, cedarUser, globalRequestId,
        localRequestId);
  }

  @Override
  public CategoryPermissions getCategoryPermissions(CedarCategoryId categoryId) {
    FolderServerCategory category = proxies.category().getCategoryById(categoryId);
    if (category != null) {
      FolderServerUser owner = getCategoryOwner(categoryId);
      List<FolderServerUser> attachUsers = getUsersWithDirectCategoryPermission(categoryId, CategoryPermission.ATTACH);
      List<FolderServerUser> writeUsers = getUsersWithDirectCategoryPermission(categoryId, CategoryPermission.WRITE);
      List<FolderServerGroup> attachGroups = getGroupsWithDirectCategoryPermission(categoryId,
          CategoryPermission.ATTACH);
      List<FolderServerGroup> writeGroups = getGroupsWithDirectCategoryPermission(categoryId, CategoryPermission.WRITE);
      return buildCategoryPermissions(owner, attachUsers, writeUsers, attachGroups, writeGroups);
    } else {
      return null;
    }
  }

  @Override
  public BackendCallResult updateCategoryPermissions(CedarCategoryId categoryId, CategoryPermissionRequest request) {
    CategoryPermissionRequestValidator prv = new CategoryPermissionRequestValidator(this, proxies, categoryId, request);
    BackendCallResult bcr = prv.getCallResult();
    if (bcr.isError()) {
      return bcr;
    } else {
      CategoryPermissions currentPermissions = getCategoryPermissions(categoryId);
      CategoryPermissions newPermissions = prv.getPermissions();

      CedarUserId oldOwnerId = currentPermissions.getOwner().getResourceId();
      CedarUserId newOwnerId = newPermissions.getOwner().getResourceId();
      if (oldOwnerId != null && !oldOwnerId.equals(newOwnerId)) {
        Neo4JUserSessionCategoryOperations.updateCategoryOwner(proxies.category(), categoryId, newOwnerId);
      }

      Set<CategoryPermissionUserPermissionPair> oldUserPermissions = new HashSet<>();
      for (CategoryUserPermission up : currentPermissions.getUserPermissions()) {
        oldUserPermissions.add(up.getAsUserIdPermissionPair());
      }
      Set<CategoryPermissionUserPermissionPair> newUserPermissions = new HashSet<>();
      for (CategoryUserPermission up : newPermissions.getUserPermissions()) {
        newUserPermissions.add(up.getAsUserIdPermissionPair());
      }

      Set<CategoryPermissionUserPermissionPair> toRemoveUserPermissions = new HashSet<>(oldUserPermissions);
      toRemoveUserPermissions.removeAll(newUserPermissions);
      if (!toRemoveUserPermissions.isEmpty()) {
        Neo4JUserSessionCategoryOperations.removeCategoryUserPermissions(proxies.categoryPermission(), categoryId,
            toRemoveUserPermissions);
      }

      Set<CategoryPermissionUserPermissionPair> toAddUserPermissions = new HashSet<>(newUserPermissions);
      toAddUserPermissions.removeAll(oldUserPermissions);
      if (!toAddUserPermissions.isEmpty()) {
        Neo4JUserSessionCategoryOperations.addCategoryUserPermissions(proxies.categoryPermission(), categoryId,
            toAddUserPermissions);
      }

      Set<CategoryPermissionGroupPermissionPair> oldGroupPermissions = new HashSet<>();
      for (CategoryGroupPermission gp : currentPermissions.getGroupPermissions()) {
        oldGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }
      Set<CategoryPermissionGroupPermissionPair> newGroupPermissions = new HashSet<>();
      for (CategoryGroupPermission gp : newPermissions.getGroupPermissions()) {
        newGroupPermissions.add(gp.getAsGroupIdPermissionPair());
      }

      Set<CategoryPermissionGroupPermissionPair> toRemoveGroupPermissions = new HashSet<>(oldGroupPermissions);
      toRemoveGroupPermissions.removeAll(newGroupPermissions);
      if (!toRemoveGroupPermissions.isEmpty()) {
        Neo4JUserSessionCategoryOperations.removeCategoryGroupPermissions(proxies.categoryPermission(), categoryId,
            toRemoveGroupPermissions);
      }

      Set<CategoryPermissionGroupPermissionPair> toAddGroupPermissions = new HashSet<>(newGroupPermissions);
      toAddGroupPermissions.removeAll(oldGroupPermissions);
      if (!toAddGroupPermissions.isEmpty()) {
        Neo4JUserSessionCategoryOperations.addCategoryGroupPermissions(proxies.categoryPermission(), categoryId,
            toAddGroupPermissions);
      }

      return new BackendCallResult();
    }
  }

  @Override
  public boolean userIsOwnerOfCategory(CedarCategoryId categoryId) {
    FolderServerUser owner = getCategoryOwner(categoryId);
    return owner != null && owner.getId().equals(cu.getId());
  }

  private List<FolderServerUser> getUsersWithDirectCategoryPermission(CedarCategoryId categoryId, CategoryPermission permission) {
    return proxies.categoryPermission().getUsersWithDirectPermissionOnCategory(categoryId, permission);
  }

  private List<FolderServerGroup> getGroupsWithDirectCategoryPermission(CedarCategoryId categoryId, CategoryPermission permission) {
    return proxies.categoryPermission().getGroupsWithDirectPermissionOnCategory(categoryId, permission);
  }

  private CategoryPermissions buildCategoryPermissions(FolderServerUser owner, List<FolderServerUser> attachUsers,
                                                       List<FolderServerUser> writeUsers, List<FolderServerGroup> attachGroups,
                                                       List<FolderServerGroup> writeGroups) {
    CategoryPermissions permissions = new CategoryPermissions();
    CedarUserExtract o = owner.buildExtract();
    permissions.setOwner(o);
    if (attachUsers != null) {
      for (FolderServerUser user : attachUsers) {
        CedarUserExtract u = user.buildExtract();
        CategoryUserPermission up = new CategoryUserPermission(u, CategoryPermission.ATTACH);
        permissions.addUserPermissions(up);
      }
    }
    if (writeUsers != null) {
      for (FolderServerUser user : writeUsers) {
        CedarUserExtract u = user.buildExtract();
        CategoryUserPermission up = new CategoryUserPermission(u, CategoryPermission.WRITE);
        permissions.addUserPermissions(up);
      }
    }
    if (attachGroups != null) {
      for (FolderServerGroup group : attachGroups) {
        CedarGroupExtract g = group.buildExtract();
        CategoryGroupPermission gp = new CategoryGroupPermission(g, CategoryPermission.ATTACH);
        permissions.addGroupPermissions(gp);
      }
    }
    if (writeGroups != null) {
      for (FolderServerGroup group : writeGroups) {
        CedarGroupExtract g = group.buildExtract();
        CategoryGroupPermission gp = new CategoryGroupPermission(g, CategoryPermission.WRITE);
        permissions.addGroupPermissions(gp);
      }
    }
    return permissions;
  }

  @Override
  public boolean userHasWriteAccessToCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      return proxies.categoryPermission().userHasWriteAccessToCategory(cu.getResourceId(), categoryId);
    }
  }

  @Override
  public boolean userHasAttachAccessToCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      return proxies.categoryPermission().userHasAttachAccessToCategory(cu.getResourceId(), categoryId);
    }
  }

  @Override
  public boolean userCanChangeOwnerOfCategory(CedarCategoryId categoryId) {
    if (cu.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY)) {
      return true;
    } else {
      FolderServerUser owner = getCategoryOwner(categoryId);
      return owner != null && owner.getId().equals(cu.getId());
    }
  }

  @Override
  public boolean userHas(CedarPermission permission) {
    return cu.has(permission);
  }

}
