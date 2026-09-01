package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

public class CypherQueryBuilderFilesystemResourcePermission extends AbstractCypherQueryBuilder {

  public static String getVersionedPermissions() {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH (owner:<LABEL.USER>)-[:<REL.OWNS>]->(resource)
        OPTIONAL MATCH (principal)-[grant:CANREAD|CANWRITE|CANCHANGEOWNER|CANCHANGEPERMISSIONS|CANPUBLISH|CANCREATEDRAFT]->(resource)
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
        OPTIONAL MATCH ()-[oldGrant:CANREAD|CANWRITE|CANCHANGEOWNER|CANCHANGEPERMISSIONS|CANPUBLISH|CANCREATEDRAFT]->(resource)
        WITH resource, owner, users, groups,
          collect(DISTINCT oldOwner) + collect(DISTINCT oldGrant) AS oldRelations
        FOREACH (relation IN oldRelations | DELETE relation)
        CREATE (owner)-[:<REL.OWNS>]->(resource)
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.READ_USER_ID_LIST>}] |
          CREATE (user)-[:CANREAD]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.WRITE_USER_ID_LIST>}] |
          CREATE (user)-[:CANWRITE]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.CHANGE_OWNER_USER_ID_LIST>}] |
          CREATE (user)-[:CANCHANGEOWNER]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.CHANGE_PERMISSIONS_USER_ID_LIST>}] |
          CREATE (user)-[:CANCHANGEPERMISSIONS]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.PUBLISH_USER_ID_LIST>}] |
          CREATE (user)-[:CANPUBLISH]->(resource))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.CREATE_DRAFT_USER_ID_LIST>}] |
          CREATE (user)-[:CANCREATEDRAFT]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.READ_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANREAD]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.WRITE_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANWRITE]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.CHANGE_OWNER_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANCHANGEOWNER]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.CHANGE_PERMISSIONS_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANCHANGEPERMISSIONS]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.PUBLISH_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANPUBLISH]->(resource))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.CREATE_DRAFT_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANCREATEDRAFT]->(resource))
        SET resource.<PROP.EVERYBODY_PERMISSION> = {<PH.EVERYBODY_PERMISSION>},
            resource._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH resource
        OPTIONAL MATCH (newOwner:<LABEL.USER>)-[:<REL.OWNS>]->(resource)
        OPTIONAL MATCH (principal)-[grant:CANREAD|CANWRITE|CANCHANGEOWNER|CANCHANGEPERMISSIONS|CANPUBLISH|CANCREATEDRAFT]->(resource)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN newOwner AS owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          resource._cedarAclRevision AS revision
        """;
  }

  public static String addPermissionToFilesystemResourceForUser(FilesystemResourcePermission permission) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (user)-[:%s]->(resource)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN user
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String addPermissionToFilesystemResourceForGroup(FilesystemResourcePermission permission) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (group)-[:%s]->(resource)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN group
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String removePermissionForFilesystemResourceFromUser(FilesystemResourcePermission permission) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (user)-[relation:%s]->(resource)
        DELETE (relation)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN resource
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String removePermissionForFilesystemResourceFromGroup(FilesystemResourcePermission permission) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (group)-[relation:%s]->(resource)
        DELETE (relation)
        SET resource._cedarAclRevision = coalesce(resource._cedarAclRevision, 1) + 1
        RETURN resource
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String userCanReadFilesystemResource() {
    return userHasPermissionOnFilesystemResource(RelationLabel.CANREAD);
  }

  public static String userCanWriteFilesystemResource() {
    return userHasPermissionOnFilesystemResource(RelationLabel.CANWRITE);
  }

  private static String userHasPermissionOnFilesystemResource(RelationLabel relationLabel) {
    String canLabel = RelationLabel.CANREAD + "|" + RelationLabel.CANWRITE;
    if (relationLabel == RelationLabel.CANWRITE) {
      canLabel = RelationLabel.CANWRITE.toString();
    }
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
        OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(user:User)
        WITH user, resource, p1, p2
        WHERE p1 IS NOT NULL OR p2 IS NOT NULL
        RETURN DISTINCT user
        """.formatted(canLabel);
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

  public static String getUserIdsWithTransitiveReadOnFilesystemResource() {
    return getUserIdsWithTransitivePermissionOnFilesystemResource(RelationLabel.CANREAD);
  }

  public static String getUserIdsWithTransitiveWriteOnFilesystemResource() {
    return getUserIdsWithTransitivePermissionOnFilesystemResource(RelationLabel.CANWRITE);
  }

  private static String getUserIdsWithTransitivePermissionOnFilesystemResource(RelationLabel relationLabel) {
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
        """.formatted(relationLabel);
  }

  public static String getGroupIdsWithTransitiveReadOnFilesystemResource() {
    return getGroupIdsWithTransitivePermissionOnFilesystemResource(RelationLabel.CANREAD);
  }

  public static String getGroupIdsWithTransitiveWriteOnFilesystemResource() {
    return getGroupIdsWithTransitivePermissionOnFilesystemResource(RelationLabel.CANWRITE);
  }

  public static String getGroupIdsWithTransitivePermissionOnFilesystemResource(RelationLabel relationLabel) {
    return """
        MATCH (group:<LABEL.GROUP>)-[:%s]->()-[:<REL.CONTAINS>*0..]->(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        RETURN group.<PROP.ID>
        """.formatted(relationLabel);
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
