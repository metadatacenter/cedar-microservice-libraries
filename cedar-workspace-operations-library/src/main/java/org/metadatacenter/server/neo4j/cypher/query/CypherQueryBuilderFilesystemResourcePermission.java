package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;

public class CypherQueryBuilderFilesystemResourcePermission extends AbstractCypherQueryBuilder {

  public static String getVersionedPermissions() {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH (owner:<LABEL.USER>)-[:<REL.OWNS>]->(resource)
        OPTIONAL MATCH (principal)-[grant:CANREAD|CANWRITE|EDITOR_ROLE|VIEWER_ROLE|MANAGER_ROLE]->(resource)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          coalesce(resource._cedarAclRevision, 1) AS revision
        """;
  }

  public static String lockPermissions() {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1)
        RETURN resource._cedarAclRevision AS revision
        """;
  }

  public static String transferOwnership() {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (currentOwner:<LABEL.USER> {<PROP.ID>:{<PH.OWNER_ID>}})-[ownership:<REL.OWNS>]->(resource)
        MATCH (newOwner:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        OPTIONAL MATCH (newOwner)-[newOwnerGrant:CANREAD|CANWRITE|EDITOR_ROLE|VIEWER_ROLE|MANAGER_ROLE]->(resource)
        WITH resource, ownership, newOwner, collect(newOwnerGrant) AS newOwnerGrants
        DELETE ownership
        FOREACH (grant IN newOwnerGrants | DELETE grant)
        CREATE (newOwner)-[:<REL.OWNS>]->(resource)
        SET resource._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH resource
        OPTIONAL MATCH (owner:<LABEL.USER>)-[:<REL.OWNS>]->(resource)
        OPTIONAL MATCH (principal)-[grant:CANREAD|CANWRITE|EDITOR_ROLE|VIEWER_ROLE|MANAGER_ROLE]->(resource)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          resource._cedarAclRevision AS revision
        """;
  }

  public static String replacePermissions() {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (owner:<LABEL.USER> {<PROP.ID>:{<PH.OWNER_ID>}})
        OPTIONAL MATCH (user:<LABEL.USER>)
        WHERE user.<PROP.ID> IN {<PH.USER_ID_LIST>}
        WITH resource, owner, collect(DISTINCT user) AS users
        WHERE size(users) = size({<PH.USER_ID_LIST>})
        OPTIONAL MATCH (group:<LABEL.GROUP>)
        WHERE group.<PROP.ID> IN {<PH.GROUP_ID_LIST>}
        WITH resource, owner, users, collect(DISTINCT group) AS groups
        WHERE size(groups) = size({<PH.GROUP_ID_LIST>})
        OPTIONAL MATCH ()-[oldOwner:<REL.OWNS>]->(resource)
        OPTIONAL MATCH ()-[oldGrant:CANREAD|CANWRITE|EDITOR_ROLE|VIEWER_ROLE|MANAGER_ROLE|CANCHANGEOWNER|CANCHANGEPERMISSIONS|CANPUBLISH|CANCREATEDRAFT]->(resource)
        WITH resource, owner, users, groups,
          collect(DISTINCT oldOwner) + collect(DISTINCT oldGrant) AS oldRelations
        FOREACH (relation IN oldRelations | DELETE relation)
        CREATE (owner)-[:<REL.OWNS>]->(resource)
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.VIEWER_USER_ID_LIST>}] |
          CREATE (user)-[:CANREAD]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.EDITOR_USER_ID_LIST>}] |
          CREATE (user)-[:EDITOR_ROLE]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.MANAGER_USER_ID_LIST>}] |
          CREATE (user)-[:CANWRITE]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.VIEWER_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANREAD]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.EDITOR_GROUP_ID_LIST>}] |
          CREATE (group)-[:EDITOR_ROLE]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.MANAGER_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANWRITE]->(resource))
        SET resource.<PROP.EVERYBODY_PERMISSION> = {<PH.EVERYBODY_PERMISSION>},
            resource._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH resource
        OPTIONAL MATCH (newOwner:<LABEL.USER>)-[:<REL.OWNS>]->(resource)
        OPTIONAL MATCH (principal)-[grant:CANREAD|CANWRITE|EDITOR_ROLE|VIEWER_ROLE|MANAGER_ROLE]->(resource)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN newOwner AS owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          resource._cedarAclRevision AS revision
        """;
  }

  public static String addRoleToFilesystemResourceForUser(ResourceRole role) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (user)-[:%s]->(resource)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN user
        """.formatted(RelationLabel.forResourceRole(role));
  }

  public static String addRoleToFilesystemResourceForGroup(ResourceRole role) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (group)-[:%s]->(resource)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN group
        """.formatted(RelationLabel.forResourceRole(role));
  }

  public static String removeRoleForFilesystemResourceFromUser(ResourceRole role) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (user)-[relation:%s]->(resource)
        DELETE (relation)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN resource
        """.formatted(roleLabels(role));
  }

  public static String removeRoleForFilesystemResourceFromGroup(ResourceRole role) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (group)-[relation:%s]->(resource)
        DELETE (relation)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN resource
        """.formatted(roleLabels(role));
  }

  public static String userHasViewerRoleOnFilesystemResource() {
    return userHasRoleOnFilesystemResource(ResourceRole.VIEWER);
  }

  public static String userHasEditorRoleOnFilesystemResource() {
    return userHasRoleOnFilesystemResource(ResourceRole.EDITOR);
  }

  public static String userHasManagerRoleOnFilesystemResource() {
    return userHasRoleOnFilesystemResource(ResourceRole.MANAGER);
  }

  private static String userHasRoleOnFilesystemResource(ResourceRole requiredRole) {
    String grantLabels = switch (requiredRole) {
      case VIEWER -> "CANREAD|VIEWER_ROLE|EDITOR_ROLE|CANWRITE|MANAGER_ROLE";
      case EDITOR -> "EDITOR_ROLE|CANWRITE|MANAGER_ROLE";
      case MANAGER -> "CANWRITE|MANAGER_ROLE";
    };
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
        OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(user:User)
        WITH user, resource, p1, p2
        WHERE p1 IS NOT NULL OR p2 IS NOT NULL
        RETURN DISTINCT user
        """.formatted(grantLabels);
  }

  public static String getUsersWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return """
        MATCH (user:<LABEL.USER>)
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (user)-[:%s]->(resource)
        RETURN user
        """.formatted(relationLabel);
  }

  public static String getGroupsWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return """
        MATCH (group:<LABEL.GROUP>)
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (group)-[:%s]->(resource)
        RETURN group
        """.formatted(relationLabel);
  }

  public static String getUserIdsWithTransitiveViewerRoleOnFilesystemResource() {
    return getUserIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.VIEWER);
  }

  public static String getUserIdsWithTransitiveEditorRoleOnFilesystemResource() {
    return getUserIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.EDITOR);
  }

  public static String getUserIdsWithTransitiveManagerRoleOnFilesystemResource() {
    return getUserIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.MANAGER);
  }

  private static String getUserIdsWithTransitiveRoleOnFilesystemResource(ResourceRole role) {
    // The owner traversal and the grant traversal must bind DISTINCT node variables. If both
    // OPTIONAL MATCHes bind the same `user`, the second reuses the binding the first produced, so a
    // grantee who is not also an owner is silently dropped. This is the materialized user list that
    // feeds the search index, so the effect was that a shared artifact never carried its grantee's
    // key and a name search could not find it. Collect each set on its own and union them.
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(owner:User)
        OPTIONAL MATCH (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(grantee:User)
        WITH collect(DISTINCT owner) + collect(DISTINCT grantee) AS users
        UNWIND users AS user
        RETURN DISTINCT user.<PROP.ID>
        """.formatted(roleLabels(role));
  }

  public static String getGroupIdsWithTransitiveViewerRoleOnFilesystemResource() {
    return getGroupIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.VIEWER);
  }

  public static String getGroupIdsWithTransitiveEditorRoleOnFilesystemResource() {
    return getGroupIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.EDITOR);
  }

  public static String getGroupIdsWithTransitiveManagerRoleOnFilesystemResource() {
    return getGroupIdsWithTransitiveRoleOnFilesystemResource(ResourceRole.MANAGER);
  }

  private static String getGroupIdsWithTransitiveRoleOnFilesystemResource(ResourceRole role) {
    return """
        MATCH (group:<LABEL.GROUP>)-[:%s]->()-[:<REL.CONTAINS>*0..]->(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        RETURN group.<PROP.ID>
        """.formatted(roleLabels(role));
  }

  private static String roleLabels(ResourceRole role) {
    return switch (role) {
      case VIEWER -> "CANREAD|VIEWER_ROLE";
      case EDITOR -> "EDITOR_ROLE";
      case MANAGER -> "CANWRITE|MANAGER_ROLE";
    };
  }

  public static String getTransitiveEverybodyPermission() {
    return """
        MATCH
        (parent:<LABEL.FILESYSTEM_RESOURCE>)-[:<REL.CONTAINS>*0..]->(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>}})
        WHERE parent.<PROP.EVERYBODY_PERMISSION> IS NOT NULL
        RETURN parent.<PROP.ID> AS resourceId, parent.<PROP.EVERYBODY_PERMISSION> AS everybodyPermission
        """;
  }
}
