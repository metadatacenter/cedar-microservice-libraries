package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

public class CypherQueryBuilderFilesystemResourcePermission extends AbstractCypherQueryBuilder {

  public static String addPermissionToFilesystemResourceForUser(FilesystemResourcePermission permission) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (user)-[:%s]->(resource)
        RETURN user
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String addPermissionToFilesystemResourceForGroup(FilesystemResourcePermission permission) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MERGE (group)-[:%s]->(resource)
        RETURN group
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String removePermissionForFilesystemResourceFromUser(FilesystemResourcePermission permission) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (user)-[relation:%s]->(resource)
        DELETE (relation)
        RETURN resource
        """.formatted(RelationLabel.forFilesystemResourcePermission(permission));
  }

  public static String removePermissionForFilesystemResourceFromGroup(FilesystemResourcePermission permission) {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        MATCH (group)-[relation:%s]->(resource)
        DELETE (relation)
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
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
        OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(user:User)
        WITH user, p1, p2
        WHERE p1 IS NOT NULL OR p2 IS NOT NULL
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
