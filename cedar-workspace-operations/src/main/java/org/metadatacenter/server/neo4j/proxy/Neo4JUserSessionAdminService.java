package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.FolderStructureConfig;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.neo4j.*;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Neo4JUserSessionAdminService extends AbstractNeo4JUserSession implements AdminServiceSession {

  protected static final Logger log = LoggerFactory.getLogger(Neo4JUserSessionAdminService.class);

  private Neo4JUserSessionAdminService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static AdminServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId,
                                        String localRequestId) {
    return new Neo4JUserSessionAdminService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public boolean wipeAllData() {
    return proxies.admin().wipeAllData();
  }

  @Override
  public boolean wipeAllCategories() {
    return proxies.admin().wipeAllCategories();
  }

  @Override
  public void ensureGlobalObjectsExists() {
    Neo4jConfig config = proxies.config;
    PathUtil pathUtil = proxies.pathUtil;

    boolean addAdminToEverybody = false;

    log.info("Checking/Creating Global Objects");

    CedarUserId userId = cu.getResourceId();
    log.info("Current User Id: " + userId);

    log.info("Looking for Admin User in Neo4j");
    FolderServerUser cedarAdmin = proxies.user().findUserById(userId);
    if (cedarAdmin == null) {
      log.info("Admin user not found, trying to create it");
      cedarAdmin = proxies.user().createUser(cu);
      log.info("Admin user created, returned:" + cedarAdmin);
      addAdminToEverybody = true;
    } else {
      log.info("Admin user found");
    }

    log.info("Looking for Everybody Group in Neo4j");
    FolderServerGroup everybody = proxies.group().getEverybodyGroup();
    CedarGroupId everybodyGroupId = null;
    if (everybody == null) {
      log.info("Everybody Group not found, trying to create it");
      everybodyGroupId = linkedDataUtil.buildNewLinkedDataIdObject(CedarGroupId.class);
      log.info("Everybody Group URL just generated:" + everybodyGroupId);
      everybody = proxies.group().createGroup(everybodyGroupId, config.getEverybodyGroupName(), config.getEverybodyGroupDescription(), userId,
          Neo4JFieldValues.SPECIAL_GROUP_EVERYBODY);
      log.info("Everybody Group created, returned:" + everybody);
      addAdminToEverybody = true;
    } else {
      log.info("Everybody Group found");
    }

    if (addAdminToEverybody) {
      log.info("Adding Admin user to Everybody Group");
      boolean added = proxies.user().addUserToGroup(cedarAdmin.getResourceId(), everybodyGroupId);
      log.info("Admin user added to Everybody Group, returned:" + added);
    } else {
      log.info("Adding Admin user to Everybody Group is not needed");
    }

    FolderServerFolder rootFolder = proxies.folder().findFolderByPath(config.getRootFolderPath());
    CedarFolderId rootFolderId = null;
    if (rootFolder == null) {
      rootFolder = proxies.folder().createRootFolder(userId);
    }
    if (rootFolder != null) {
      rootFolderId = rootFolder.getResourceId();
    }

    FolderServerFolder usersFolder = proxies.folder().findFolderByPath(config.getUsersFolderPath());
    if (usersFolder == null) {
      String name = pathUtil.extractName(config.getUsersFolderPath());

      FolderServerFolder newUsersFolder = new FolderServerFolder();
      newUsersFolder.setName(name);
      newUsersFolder.setDescription(config.getUsersFolderDescription());
      newUsersFolder.setCreatedByTotal(cedarAdmin.getResourceId());
      newUsersFolder.setRoot(false);
      newUsersFolder.setSystem(true);
      newUsersFolder.setUserHome(false);

      proxies.folder().createFolderAsChildOfId(newUsersFolder, rootFolderId);
    }

    log.info("Looking for Root Category in Neo4j");
    FolderServerCategory rootCategory = proxies.category().getRootCategory();
    if (rootCategory == null) {
      log.info("Root Category not found, trying to create it");
      String rootCategoryId = linkedDataUtil.buildNewLinkedDataId(CedarResourceType.CATEGORY);
      CedarCategoryId ccRootId;
      ccRootId = CedarCategoryId.build(rootCategoryId);
      log.info("Root Category URL just generated:" + ccRootId.getId());
      rootCategory = proxies.category().createCategory(null, ccRootId, config.getRootCategoryName(),
          config.getRootCategoryDescription(), config.getRootCategoryIdentifier(), userId);
      log.info("Root Category created, returned:" + rootCategory);
    } else {
      log.info("Root Category found");
    }
  }

  @Override
  public void ensureCaDSRObjectsExists(CedarUser caDSRAdmin, UserServiceSession userSession) {

    log.info("Adding caDSR Admin user to Everybody Group");
    FolderServerGroup everybody = proxies.group().getEverybodyGroup();
    boolean added = proxies.user().addUserToGroup(caDSRAdmin.getResourceId(), everybody.getResourceId());
    log.info("caDSR Admin user added to Everybody Group, returned:" + added);

    log.info("Looking for home folder of caDSR Admin");

    FolderServerFolder currentUserHomeFolder = proxies.folder().findHomeFolderOf(caDSRAdmin.getResourceId());
    if (currentUserHomeFolder == null) {
      log.info("Creating home folder of caDSR Admin");
      FolderServiceSession folderServiceSession = Neo4JUserSessionFolderService.get(cedarConfig, proxies, caDSRAdmin, null, null);
      currentUserHomeFolder = folderServiceSession.createUserHomeFolder();
    }

    log.info("Linking home folder of caDSR Admin");
    caDSRAdmin.setHomeFolderId(currentUserHomeFolder.getId());
    proxies.user().updateUser(caDSRAdmin);
    proxies.folder().setOwner(currentUserHomeFolder.getResourceId(), caDSRAdmin.getResourceId());

    // TODO: refactor this, present above as well
    log.info("Looking for Root Category in Neo4j");
    FolderServerCategory rootCategory = proxies.category().getRootCategory();
    if (rootCategory == null) {
      log.info("Root Category not found, trying to create it");
      String rootCategoryId = linkedDataUtil.buildNewLinkedDataId(CedarResourceType.CATEGORY);
      CedarCategoryId ccRootId;
      ccRootId = CedarCategoryId.build(rootCategoryId);
      log.info("Root Category URL just generated:" + ccRootId.getId());
      Neo4jConfig config = proxies.config;
      CedarUserId userId = cu.getResourceId();
      rootCategory = proxies.category().createCategory(null, ccRootId, config.getRootCategoryName(),
          config.getRootCategoryDescription(), config.getRootCategoryIdentifier(), userId);
      log.info("Root Category created, returned:" + rootCategory);
    } else {
      log.info("Root Category found");
    }

    FolderStructureConfig config = cedarConfig.getFolderStructureConfig();
    FolderServerCategory caDSRRootCategory = proxies.category().getCategoryByIdentifier(config.getCaDSRRootCategory().getIdentifier());
    if (caDSRRootCategory == null) {
      log.info("caDSR root Category not found, trying to create it");
      String cadsrRootCategoryId = linkedDataUtil.buildNewLinkedDataId(CedarResourceType.CATEGORY);
      CedarCategoryId ccRootId;
      ccRootId = CedarCategoryId.build(cadsrRootCategoryId);
      log.info("caDSR root Category URL just generated:" + ccRootId.getId());
      caDSRRootCategory = proxies.category().createCategory(rootCategory.getResourceId(), ccRootId, config.getCaDSRRootCategory().getName(),
          config.getCaDSRRootCategory().getDescription(), config.getCaDSRRootCategory().getIdentifier(), cu.getResourceId());
      log.info("caDSR root Category created, returned:" + rootCategory);
    } else {
      log.info("caDSR root Category found");
    }

    proxies.categoryPermission().addCategoryPermissionToUser(caDSRRootCategory.getResourceId(), caDSRAdmin.getResourceId(), CategoryPermission.WRITE);

  }

  @Override
  public boolean createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property) {
    return proxies.admin().createUniqueConstraint(nodeLabel, property);
  }

  @Override
  public boolean createIndex(NodeLabel nodeLabel, NodeProperty property) {
    return proxies.admin().createIndex(nodeLabel, property);
  }

  @Override
  public boolean removeAllConstraintsAndIndices() {
    return proxies.admin().removeAllConstraintsAndIndices();
  }
}
