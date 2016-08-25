package org.metadatacenter.server.neo4j;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.*;
import org.metadatacenter.server.security.model.auth.*;
import org.metadatacenter.server.security.model.user.CedarGroupExtract;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.util.*;

public class Neo4JUserSession {
  private CedarUser cu;
  private Neo4JProxy neo4JProxy;
  private String userIdPrefix;
  private String groupIdPrefix;

  public Neo4JUserSession(Neo4JProxy neo4JProxy, CedarUser cu) {
    this.neo4JProxy = neo4JProxy;
    this.cu = cu;
    this.userIdPrefix = neo4JProxy.getUserIdPrefix();
    this.groupIdPrefix = neo4JProxy.getGroupIdPrefix();
  }

  public static Neo4JUserSession get(Neo4JProxy neo4JProxy, UserService userService, CedarUser cu, boolean createHome) {
    Neo4JUserSession neo4JUserSession = new Neo4JUserSession(neo4JProxy, cu);
    if (createHome) {
      CedarFSUser createdUser = neo4JUserSession.ensureUserExists();
      CedarFSFolder createdFolder = neo4JUserSession.ensureUserHomeExists();
      if (createdFolder != null) {
        ObjectNode homeModification = JsonMapper.MAPPER.createObjectNode();
        homeModification.put("homeFolderId", createdFolder.getId());
        System.out.println("homeModification: " + homeModification);
        try {
          userService.updateUser(cu.getUserId(), homeModification);
          System.out.println("User updated");
        } catch (Exception e) {
          System.out.println("Error while updating the user:");
          e.printStackTrace();
        }
      }
    }
    return neo4JUserSession;
  }

  private String getUserId() {
    // let the NPE, something is really wrong if that happens
    return userIdPrefix + cu.getUserId();
  }

  private String buildGroupId(String groupUUID) {
    return groupIdPrefix + groupUUID;
  }

  // Expose methods of PathUtil
  public String sanitizeName(String name) {
    return neo4JProxy.getPathUtil().sanitizeName(name);
  }

  public String normalizePath(String path) {
    return neo4JProxy.getPathUtil().normalizePath(path);
  }

  public String getChildPath(String path, String name) {
    return neo4JProxy.getPathUtil().getChildPath(path, name);
  }

  public String getRootPath() {
    return neo4JProxy.getPathUtil().getRootPath();
  }

  public String getResourceUUID(String resourceId, CedarNodeType nodeType) {
    return neo4JProxy.getResourceUUID(resourceId, nodeType);
  }

  public CedarFSFolder findFolderById(String folderURL) {
    return neo4JProxy.findFolderById(folderURL);
  }

  public CedarFSNode findNodeById(String nodeURL) {
    return neo4JProxy.findNodeById(nodeURL);
  }

  private CedarFSUser getNodeOwner(String nodeURL) {
    return neo4JProxy.getNodeOwner(nodeURL);
  }

  private List<CedarFSUser> getUsersWithPermission(String nodeURL, NodePermission permission) {
    return neo4JProxy.getUsersWithPermissionOnNode(nodeURL, permission);
  }

  private List<CedarFSGroup> getGroupsWithPermission(String nodeURL, NodePermission permission) {
    return neo4JProxy.getGroupsWithPermissionOnNode(nodeURL, permission);
  }

  public List<CedarFSNode> findAllNodes(int limit, int offset, List<String> sortList) {
    return neo4JProxy.findAllNodes(limit, offset, sortList);
  }

  public long findAllNodesCount() {
    return neo4JProxy.findAllNodesCount();
  }

  public CedarFSResource findResourceById(String resourceURL) {
    return neo4JProxy.findResourceById(resourceURL);
  }

  public CedarFSFolder createFolderAsChildOfId(String parentFolderURL, String name, String displayName, String
      description, NodeLabel label) {
    return createFolderAsChildOfId(parentFolderURL, name, displayName, description, label, null);
  }

  public CedarFSFolder createFolderAsChildOfId(String parentFolderURL, String name, String displayName, String
      description, NodeLabel label, Map<String, Object> extraProperties) {
    return neo4JProxy.createFolderAsChildOfId(parentFolderURL, name, displayName, description,
        getUserId(), label, extraProperties);
  }

  public CedarFSResource createResourceAsChildOfId(String parentFolderURL, String childURL, CedarNodeType
      nodeType, String name, String description, NodeLabel label) {
    return createResourceAsChildOfId(parentFolderURL, childURL, nodeType, name, description, label, null);
  }

  public CedarFSResource createResourceAsChildOfId(String parentFolderURL, String childURL, CedarNodeType
      nodeType, String name, String description, NodeLabel label, Map<String, Object> extraProperties) {
    return neo4JProxy.createResourceAsChildOfId(parentFolderURL, childURL, nodeType, name,
        description, getUserId(), label, extraProperties);
  }

  public CedarFSFolder updateFolderById(String folderURL, Map<String, String> updateFields) {
    return neo4JProxy.updateFolderById(folderURL, updateFields, getUserId());
  }

  public CedarFSResource updateResourceById(String resourceURL, CedarNodeType nodeType, Map<String,
      String> updateFields) {
    return neo4JProxy.updateResourceById(resourceURL, updateFields, getUserId());
  }

  public boolean deleteFolderById(String folderURL) {
    return neo4JProxy.deleteFolderById(folderURL);
  }

  public boolean deleteResourceById(String resourceURL, CedarNodeType nodeType) {
    return neo4JProxy.deleteResourceById(resourceURL);
  }

  public CedarFSFolder findFolderByPath(String path) {
    return neo4JProxy.findFolderByPath(path);
  }

  public CedarFSFolder findFolderByParentIdAndName(CedarFSFolder parentFolder, String name) {
    return neo4JProxy.findFolderByParentIdAndName(parentFolder.getId(), name);
  }

  public CedarFSNode findNodeByParentIdAndName(CedarFSFolder parentFolder, String name) {
    return neo4JProxy.findNodeByParentIdAndName(parentFolder.getId(), name);
  }

  public List<CedarFSFolder> findFolderPathByPath(String path) {
    return neo4JProxy.findFolderPathByPath(path);
  }


  public List<CedarFSFolder> findFolderPath(CedarFSFolder folder) {
    if (folder.isRoot()) {
      List<CedarFSFolder> pathInfo = new ArrayList<>();
      pathInfo.add(folder);
      return pathInfo;
    } else {
      return neo4JProxy.findFolderPathById(folder.getId());
    }
  }

  public List<CedarFSNode> findFolderContents(String folderURL, List<CedarNodeType> nodeTypeList, int
      limit, int offset, List<String> sortList) {
    return neo4JProxy.findFolderContents(folderURL, nodeTypeList, limit, offset, sortList, cu);
  }

  public long findFolderContentsCount(String folderURL) {
    return neo4JProxy.findFolderContentsCount(folderURL);
  }

  public long findFolderContentsCount(String folderURL, List<CedarNodeType> nodeTypeList) {
    return neo4JProxy.findFolderContentsFilteredCount(folderURL, nodeTypeList);
  }

  public void ensureGlobalObjectsExists() {
    Neo4jConfig config = neo4JProxy.getConfig();
    IPathUtil pathUtil = neo4JProxy.getPathUtil();

    CedarFSGroup everybody = neo4JProxy.findGroupBySpecialValue(Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
    if (everybody == null) {
      String everybodyURL = buildGroupId(UUID.randomUUID().toString());
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put(Neo4JFields.SPECIAL_GROUP, Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
      everybody = neo4JProxy.createGroup(everybodyURL, config.getEverybodyGroupName(),
          config.getEverybodyGroupDisplayName(), extraParams);
    }

    String userId = getUserId();

    CedarFSUser cedarAdmin = neo4JProxy.findUserById(userId);
    if (cedarAdmin == null) {
      cedarAdmin = neo4JProxy.createUser(userId, cu.getScreenName(), cu.getScreenName(), cu.getFirstName(), cu
          .getLastName(), everybody);
    }

    CedarFSFolder rootFolder = findFolderByPath(config.getRootFolderPath());
    String rootFolderURL = null;
    if (rootFolder == null) {
      rootFolder = neo4JProxy.createRootFolder(userId);
      neo4JProxy.addPermission(rootFolder, everybody, NodePermission.READTHIS);
    }
    if (rootFolder != null) {
      rootFolderURL = rootFolder.getId();
    }

    CedarFSFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
    if (usersFolder == null) {
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put(Neo4JFields.IS_SYSTEM, true);
      String name = pathUtil.extractName(config.getUsersFolderPath());
      usersFolder = createFolderAsChildOfId(rootFolderURL, name, name, config.getUsersFolderDescription(), NodeLabel
          .SYSTEM_FOLDER, extraParams);
      neo4JProxy.addPermission(usersFolder, everybody, NodePermission.READTHIS);
    }

    CedarFSFolder lostAndFoundFolder = findFolderByPath(config.getLostAndFoundFolderPath());
    if (lostAndFoundFolder == null) {
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put(Neo4JFields.IS_SYSTEM, true);
      String name = pathUtil.extractName(config.getLostAndFoundFolderPath());
      lostAndFoundFolder = createFolderAsChildOfId(rootFolderURL, name, name, config.getLostAndFoundFolderDescription
              (), NodeLabel.SYSTEM_FOLDER,
          extraParams);
      neo4JProxy.addPermission(lostAndFoundFolder, everybody, NodePermission.READTHIS);
    }
  }

  public CedarFSFolder ensureUserHomeExists() {
    Neo4jConfig config = neo4JProxy.getConfig();
    IPathUtil pathUtil = neo4JProxy.getPathUtil();
    String userHomePath = config.getUsersFolderPath() + pathUtil.getSeparator() + cu.getUserId();
    CedarFSFolder currentUserHomeFolder = findFolderByPath(userHomePath);
    if (currentUserHomeFolder == null) {
      CedarFSFolder usersFolder = findFolderByPath(config.getUsersFolderPath());
      // usersFolder should not be null at this point. If it is, we let the NPE to be thrown
      Map<String, Object> extraParams = new HashMap<>();
      extraParams.put(Neo4JFields.IS_USER_HOME, true);
      String name = cu.getUserId();
      String displayName = CedarUserUtil.buildScreenName(cu);
      String description = CedarUserUtil.buildHomeFolderDescription(cu);
      currentUserHomeFolder = createFolderAsChildOfId(usersFolder.getId(), name, displayName, description, NodeLabel
          .USER_HOME_FOLDER, extraParams);
      if (currentUserHomeFolder != null) {
        CedarFSGroup everybody = neo4JProxy.findGroupBySpecialValue(Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
        if (everybody != null) {
          neo4JProxy.addPermission(currentUserHomeFolder, everybody, NodePermission.READTHIS);
        }
        return currentUserHomeFolder;
      }
    }
    return null;
  }

  public CedarFSUser ensureUserExists() {
    CedarFSUser currentUser = neo4JProxy.findUserById(getUserId());
    if (currentUser == null) {
      currentUser = neo4JProxy.createUser(getUserId(), cu.getScreenName(), cu.getScreenName(), cu.getFirstName(), cu
          .getLastName());
      CedarFSGroup everybody = neo4JProxy.findGroupBySpecialValue(Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
      neo4JProxy.addGroupToUser(currentUser, everybody);
    }
    return currentUser;
  }

  public void addPathAndParentId(CedarFSFolder folder) {
    if (folder != null) {
      List<CedarFSFolder> path = findFolderPath(folder);
      if (path != null) {
        folder.setPath(getPathString(path));
        folder.setParentPath(getParentPathString(path));
      }
    }
  }

  public void addPathAndParentId(CedarFSResource resource) {
    if (resource != null) {
      List<CedarFSNode> path = neo4JProxy.findNodePathById(resource.getId());
      if (path != null) {
        resource.setPath(getPathString(path));
        resource.setParentPath(getParentPathString(path));
      }
    }
  }

  private String getParentId(List<? extends CedarFSNode> path) {
    if (path != null) {
      if (path.size() > 1) {
        return path.get(path.size() - 2).getId();
      }
    }
    return null;
  }

  private String getParentPathString(List<? extends CedarFSNode> path) {
    List<CedarFSNode> p = new ArrayList<>();
    p.addAll(path);
    if (path.size() > 0) {
      p.remove(p.size() - 1);
    } else {
      return null;
    }
    return getPathString(p);
  }

  private String getPathString(List<? extends CedarFSNode> path) {
    StringBuilder sb = new StringBuilder();
    boolean addSeparator = false;
    for (CedarFSNode node : path) {
      if (addSeparator) {
        sb.append(neo4JProxy.getPathUtil().getSeparator());
      }
      if (node instanceof CedarFSFolder) {
        if (!((CedarFSFolder) node).isRoot()) {
          addSeparator = true;
        }
      }
      sb.append(node.getName());
    }
    return sb.length() == 0 ? null : sb.toString();
  }

  public boolean wipeAllData() {
    return neo4JProxy.wipeAllData();
  }

  public String getHomeFolderPath() {
    Neo4jConfig config = this.neo4JProxy.getConfig();
    IPathUtil pathUtil = this.neo4JProxy.getPathUtil();
    return config.getUsersFolderPath() + pathUtil.getSeparator() + this.cu.getUserId();
  }

  public CedarNodePermissions getNodePermissions(String nodeURL) {
    CedarFSNode node = findNodeById(nodeURL);
    if (node != null) {
      CedarFSUser owner = getNodeOwner(nodeURL);
      List<CedarFSUser> readUsers = getUsersWithPermission(nodeURL, NodePermission.READ);
      List<CedarFSUser> writeUsers = getUsersWithPermission(nodeURL, NodePermission.WRITE);
      List<CedarFSGroup> readGroups = getGroupsWithPermission(nodeURL, NodePermission.READ);
      List<CedarFSGroup> writeGroups = getGroupsWithPermission(nodeURL, NodePermission.WRITE);
      return buildPermissions(owner, readUsers, writeUsers, readGroups, writeGroups);
    } else {
      return null;
    }
  }

  private CedarNodePermissions buildPermissions(CedarFSUser owner, List<CedarFSUser> readUsers, List<CedarFSUser>
      writeUsers, List<CedarFSGroup> readGroups, List<CedarFSGroup> writeGroups) {
    CedarNodePermissions permissions = new CedarNodePermissions();
    CedarUserExtract o = new CedarUserExtract(owner.getId(), owner.getFirstName(), owner.getLastName(), CedarUserUtil
        .buildScreenName(owner));
    permissions.setOwner(o);
    if (readUsers != null) {
      for (CedarFSUser user : readUsers) {
        CedarUserExtract u = new CedarUserExtract(user.getId(), user.getFirstName(), user.getLastName(),
            CedarUserUtil.buildScreenName(owner));
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, NodePermission.READ);
        permissions.addUserPermissions(up);
      }
    }
    if (writeUsers != null) {
      for (CedarFSUser user : writeUsers) {
        CedarUserExtract u = new CedarUserExtract(user.getId(), user.getFirstName(), user.getLastName(),
            CedarUserUtil.buildScreenName(owner));
        CedarNodeUserPermission up = new CedarNodeUserPermission(u, NodePermission.WRITE);
        permissions.addUserPermissions(up);
      }
    }
    if (readGroups != null) {
      for (CedarFSGroup group : readGroups) {
        CedarGroupExtract g = new CedarGroupExtract(group.getId(), group.getDisplayName());
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, NodePermission.READ);
        permissions.addGroupPermissions(gp);
      }
    }
    if (writeGroups != null) {
      for (CedarFSGroup group : writeGroups) {
        CedarGroupExtract g = new CedarGroupExtract(group.getId(), group.getDisplayName());
        CedarNodeGroupPermission gp = new CedarNodeGroupPermission(g, NodePermission.WRITE);
        permissions.addGroupPermissions(gp);
      }
    }
    return permissions;
  }

  public CedarNodePermissions updateNodePermissions(String nodeURL, CedarNodePermissionsRequest permissionsRequest) {
    CedarNodePermissions currentPermissions = getNodePermissions(nodeURL);
    String oldOwnerId = currentPermissions.getOwner().getUserId();
    String newOwnerId = permissionsRequest.getOwner();
    if (oldOwnerId != null && !oldOwnerId.equals(newOwnerId)) {
      updateNodeOwner(nodeURL, newOwnerId);
    }

    Set<String> oldUserPermissionKeys = new HashSet<>();
    for(CedarNodeUserPermission up : currentPermissions.getUserPermissions().values()) {
      oldUserPermissionKeys.add(up.getKey());
    }
    Set<String> newUserPermissionKeys = new HashSet<>();
    for(String userId : permissionsRequest.getUserPermissions().keySet()) {
      NodePermission permission = permissionsRequest.getUserPermissions().get(userId);
      newUserPermissionKeys.add(CedarNodeUserPermission.getKey(userId, permission));
    }

    Set<String> toRemoveUserPermissionKeys = new HashSet<>();
    toRemoveUserPermissionKeys.addAll(oldUserPermissionKeys);
    toRemoveUserPermissionKeys.removeAll(newUserPermissionKeys);
    if (!toRemoveUserPermissionKeys.isEmpty()) {
      removeUserPermissions(nodeURL, toRemoveUserPermissionKeys);
    }

    Set<String> toAddUserPermissionKeys = new HashSet<>();
    toAddUserPermissionKeys.addAll(newUserPermissionKeys);
    toAddUserPermissionKeys.removeAll(oldUserPermissionKeys);
    if (!toAddUserPermissionKeys.isEmpty()) {
      addUserPermissions(nodeURL, toAddUserPermissionKeys);
    }

    Set<String> oldGroupPermissionKeys = new HashSet<>();
    for(CedarNodeGroupPermission gp : currentPermissions.getGroupPermissions().values()) {
      oldGroupPermissionKeys.add(gp.getKey());
    }
    Set<String> newGroupPermissionKeys = new HashSet<>();
    for(String groupId : permissionsRequest.getGroupPermissions().keySet()) {
      NodePermission permission = permissionsRequest.getGroupPermissions().get(groupId);
      newGroupPermissionKeys.add(CedarNodeGroupPermission.getKey(groupId, permission));
    }

    Set<String> toRemoveGroupPermissionKeys = new HashSet<>();
    toRemoveGroupPermissionKeys.addAll(oldGroupPermissionKeys);
    toRemoveGroupPermissionKeys.removeAll(newGroupPermissionKeys);
    if (!toRemoveGroupPermissionKeys.isEmpty()) {
      removeGroupPermissions(nodeURL, toRemoveGroupPermissionKeys);
    }

    Set<String> toAddGroupPermissionKeys = new HashSet<>();
    toAddGroupPermissionKeys.addAll(newGroupPermissionKeys);
    toAddGroupPermissionKeys.removeAll(oldGroupPermissionKeys);
    if (!toAddGroupPermissionKeys.isEmpty()) {
      addGroupPermissions(nodeURL, toAddGroupPermissionKeys);
    }

    return getNodePermissions(nodeURL);
  }

  private void addGroupPermissions(String nodeURL, Set<String> toAddGroupPermissionKeys) {
    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%");
    System.out.println("addGroupPermissions");
    System.out.println(nodeURL);
    for(String key : toAddGroupPermissionKeys) {
      System.out.println(CedarNodeUserPermission.getId(key));
    }
  }

  private void removeGroupPermissions(String nodeURL, Set<String> toRemoveGroupPermissionKeys) {
    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%");
    System.out.println("removeGroupPermissions");
    System.out.println(nodeURL);
    for(String key : toRemoveGroupPermissionKeys) {
      System.out.println(CedarNodeUserPermission.getId(key));
    }
  }

  private void addUserPermissions(String nodeURL, Set<String> toAddUserPermissionKeys) {
    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%");
    System.out.println("addUserPermissions");
    System.out.println(nodeURL);
    for(String key : toAddUserPermissionKeys) {
      System.out.println(CedarNodeUserPermission.getId(key));
    }
  }

  private void removeUserPermissions(String nodeURL, Set<String> toRemoveUserPermissionKeys) {
    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%");
    System.out.println("toRemoveUserPermissionKeys");
    System.out.println(nodeURL);
    for(String key : toRemoveUserPermissionKeys) {
      System.out.println(CedarNodeUserPermission.getId(key));
    }
  }

  private void updateNodeOwner(String nodeURL, String newOwnerId) {
    System.out.println("%%%%%%%%%%%%%%%%%%%%%%%%%");
    System.out.println("updateNodeOwner");
    System.out.println(nodeURL);
    System.out.println(newOwnerId);
  }

}
