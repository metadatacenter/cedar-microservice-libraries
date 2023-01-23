package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;

public class CypherQueryBuilderFilesystemResource extends AbstractCypherQueryBuilder {

  public static String getOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (user)-[:<REL.OWNS>]->(resource)" +
        " RETURN user";
  }

  public static String setEverybodyPermission() {
    return "" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " SET resource.<PROP.EVERYBODY_PERMISSION> = {<PH.EVERYBODY_PERMISSION>}" +
        " RETURN resource";
  }

  public static String getAllDescendantResources() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (child:<LABEL.FILESYSTEM_RESOURCE>)" +
        " MATCH (parent)-[:<REL.CONTAINS>*0..]->(child)" +
        " RETURN child";
  }

  public static String getResourceByParentIdAndName() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (child:<LABEL.FILESYSTEM_RESOURCE>)" +
        " MATCH (parent)-[:<REL.CONTAINS>]->(child)" +
        " WHERE child.<PROP.NAME> = {<PH.NAME>}" +
        " RETURN child";
  }

  public static String getAllResourcesLookupQuery(List<String> sortList) {
    return "" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE>)" +
        " RETURN resource" +
        " ORDER BY " + getOrderByExpression("resource", sortList) +
        " SKIP $offset" +
        " LIMIT $limit";
  }

  public static String getAllResourceCountQuery() {
    return "" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE>)" +
        " RETURN count(resource)";
  }

  public static String getResourceLookupQueryById() {
    return "" +
        " MATCH (root:<LABEL.FOLDER> {<PROP.NAME>:{<PH.NAME>}})," +
        " (current:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>} })," +
        " path=shortestPath((root)-[:<REL.CONTAINS>*]->(current))" +
        " RETURN path";
  }

  public static String getSharedWithEverybodyLookupQuery(ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                         List<String> sortList) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE>)" +
            " WHERE resource.<PROP.EVERYBODY_PERMISSION> IS NOT NULL" +
            " AND resource.<PROP.RESOURCE_TYPE> in $resourceTypeList" +
            " AND (resource.<PROP.IS_USER_HOME> IS NULL OR resource.<PROP.IS_USER_HOME> <> true) "
    );
    if (version != null && version != ResourceVersionFilter.ALL) {
      sb.append(getVersionConditions(version, " AND ", "resource"));
    }
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      sb.append(getPublicationStatusConditions(" AND ", "resource"));
    }
    sb.append(" RETURN DISTINCT(resource)");
    sb.append(" ORDER BY resource.<PROP.NODE_SORT_ORDER>,");
    sb.append(getOrderByExpression("resource", sortList));
    sb.append(", resource.<PROP.VERSION> DESC");
    sb.append(" SKIP $offset");
    sb.append(" LIMIT $limit");
    return sb.toString();
  }

  public static String getSharedWithEverybodyCountQuery(ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE>)" +
            " WHERE resource.<PROP.EVERYBODY_PERMISSION> IS NOT NULL" +
            " AND resource.<PROP.RESOURCE_TYPE> in $resourceTypeList" +
            " AND (resource.<PROP.IS_USER_HOME> IS NULL OR resource.<PROP.IS_USER_HOME> <> true) "
    );
    if (version != null && version != ResourceVersionFilter.ALL) {
      sb.append(getVersionConditions(version, " AND ", "resource"));
    }
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      sb.append(getPublicationStatusConditions(" AND ", "resource"));
    }
    sb.append(" RETURN count(resource)");
    return sb.toString();
  }

  public static String getAllVisibleByGroupQuery() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (resource:<LABEL.FILESYSTEM_RESOURCE>)" +
        " WHERE" +
        " (" +
        " (group)-[:<REL.CANREAD>]->()-[:<REL.CONTAINS>*0..]->(resource)" +
        " OR " +
        " (group)-[:<REL.CANWRITE>]->()-[:<REL.CONTAINS>*0..]->(resource)" +
        " )" +
        " RETURN resource";
  }
}
