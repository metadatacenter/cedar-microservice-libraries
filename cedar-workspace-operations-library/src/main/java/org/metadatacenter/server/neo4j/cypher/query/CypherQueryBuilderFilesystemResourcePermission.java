package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;

public class CypherQueryBuilderFilesystemResourcePermission extends AbstractCypherQueryBuilder {

  public static String addPermissionToFilesystemResourceForUser(FilesystemResourcePermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MERGE (user)-[:" + RelationLabel.forFilesystemResourcePermission(permission) + "]->(resource)" +
        " RETURN user";
  }

  public static String addPermissionToFilesystemResourceForGroup(FilesystemResourcePermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MERGE (group)-[:" + RelationLabel.forFilesystemResourcePermission(permission) + "]->(resource)" +
        " RETURN group";
  }

  public static String removePermissionForFilesystemResourceFromUser(FilesystemResourcePermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MATCH (user)-[relation:" + RelationLabel.forFilesystemResourcePermission(permission) + "]->(resource)" +
        " DELETE (relation)" +
        " RETURN resource";
  }

  public static String removePermissionForFilesystemResourceFromGroup(FilesystemResourcePermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>} })" +
        " MATCH (group)-[relation:" + RelationLabel.forFilesystemResourcePermission(permission) + "]->(resource)" +
        " DELETE (relation)" +
        " RETURN resource";
  }

  public static String userCanReadFilesystemResource() {
    return userHasPermissionOnFilesystemResource(RelationLabel.CANREAD);
  }

  public static String userCanWriteFilesystemResource() {
    return userHasPermissionOnFilesystemResource(RelationLabel.CANWRITE);
  }

  private static String userHasPermissionOnFilesystemResource(RelationLabel relationLabel) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    sb.append(" MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})");
    sb.append(" WHERE");

    sb.append(" (");
    sb.append(getUserToResourceRelationWithContains(RelationLabel.OWNS, "resource"));
    if (relationLabel == RelationLabel.CANREAD) {
      sb.append(" OR ");
      sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANREAD, "resource"));
    }
    sb.append(" OR ");
    sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANWRITE, "resource"));
    sb.append(" )");
    sb.append(" RETURN user");
    return sb.toString();
  }

  public static String getUsersWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MATCH (user)-[:" + relationLabel + "]->(resource)" +
        " RETURN user";
  }

  public static String getUserIdsWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MATCH (user)-[:" + relationLabel + "]->(resource)" +
        " RETURN user.<PROP.ID>";
  }

  public static String getGroupsWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return "" +
        " MATCH (group:<LABEL.GROUP>)" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MATCH (group)-[:" + relationLabel + "]->(resource)" +
        " RETURN group";
  }

  public static String getGroupIdsWithDirectPermissionOnFilesystemResource(RelationLabel relationLabel) {
    return "" +
        " MATCH (group:<LABEL.GROUP>)" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})" +
        " MATCH (group)-[:" + relationLabel + "]->(resource)" +
        " RETURN group.<PROP.ID>";
  }

  public static String getUsersWithTransitiveReadOnFilesystemResource() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER>)");
    sb.append(" MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})");
    sb.append(" WHERE");

    sb.append(" (");
    sb.append(getUserToResourceRelationWithContains(RelationLabel.OWNS, "resource"));
    sb.append(" OR ");
    sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANREAD, "resource"));
    sb.append(" )");
    sb.append(" RETURN user");
    return sb.toString();
  }

  public static String getUserIdsWithTransitiveReadOnFilesystemResource() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER>)");
    sb.append(" MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})");
    sb.append(" WHERE");

    sb.append(" (");
    sb.append(getUserToResourceRelationWithContains(RelationLabel.OWNS, "resource"));
    sb.append(" OR ");
    sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANREAD, "resource"));
    sb.append(" )");
    sb.append(" RETURN user.<PROP.ID>");
    return sb.toString();
  }

  public static String getUserIdsWithTransitiveWriteOnFilesystemResource() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER>)");
    sb.append(" MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})");
    sb.append(" WHERE");

    sb.append(" (");
    sb.append(getUserToResourceRelationWithContains(RelationLabel.OWNS, "resource"));
    sb.append(" OR ");
    sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANWRITE, "resource"));
    sb.append(" )");
    sb.append(" RETURN user.<PROP.ID>");
    return sb.toString();
  }

  public static String getGroupIdsWithTransitiveReadOnFilesystemResource() {
    String node = "(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})";

    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (group:<LABEL.GROUP>)");
    sb.append(" -[:<REL.CANREAD>]->()-[:<REL.CONTAINS>*0..]->");
    sb.append(node);
    sb.append(" RETURN group.<PROP.ID>");

    return sb.toString();
  }

  public static String getGroupIdsWithTransitiveWriteOnFilesystemResource() {
    String node = "(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})";

    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (group:<LABEL.GROUP>)");
    sb.append(" -[:<REL.CANWRITE>]->()-[:<REL.CONTAINS>*0..]->");
    sb.append(node);
    sb.append(" RETURN group.<PROP.ID>");
    return sb.toString();
  }

  public static String getTransitiveEverybodyPermission() {
    return "" +
        "MATCH" +
        " (parent:<LABEL.FILESYSTEM_RESOURCE>)-[:<REL.CONTAINS>*0..]->(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " WHERE parent.<PROP.EVERYBODY_PERMISSION> IS NOT NULL" +
        " RETURN parent.<PROP.ID> AS resourceId, parent.<PROP.EVERYBODY_PERMISSION> AS everybodyPermission";
  }
}
