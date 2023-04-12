package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;

public class CypherQueryBuilderResource extends AbstractCypherQueryBuilder {

  public static String getSharedWithMeLookupQuery(ResourceVersionFilter version,
                                                  ResourcePublicationStatusFilter publicationStatus,
                                                  List<String> sortList) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})-" +
            "[:<REL.MEMBEROF>*0..1]->" +
            "()-" +
            "[:<REL.CANREAD>|:<REL.CANWRITE>]->" +
            "(resource)" +
            " WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList" +
            " AND resource.<PROP.EVERYBODY_PERMISSION> IS NULL" +
            " AND resource.<PROP.OWNED_BY> <> {<PH.USER_ID>}" +
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

  public static String getSharedWithMeCountQuery(ResourceVersionFilter version,
                                                 ResourcePublicationStatusFilter publicationStatus) {
    StringBuilder sb = new StringBuilder();
    sb.append(
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})-" +
            "[:<REL.MEMBEROF>*0..1]->" +
            "()-" +
            "[:<REL.CANREAD>|:<REL.CANWRITE>]->" +
            "(resource)" +
            " WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList" +
            " AND resource.<PROP.EVERYBODY_PERMISSION> IS NULL" +
            " AND resource.<PROP.OWNED_BY> <> {<PH.USER_ID>}" +
            " AND (resource.<PROP.IS_USER_HOME> IS NULL OR resource.<PROP.IS_USER_HOME> <> true) "
    );
    if (version != null && version != ResourceVersionFilter.ALL) {
      sb.append(getVersionConditions(version, " AND ", "resource"));
    }
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      sb.append(getPublicationStatusConditions(" AND ", "resource"));
    }
    sb.append(
        " RETURN count(resource)"
    );
    return sb.toString();
  }

  public static String getAllLookupQuery(ResourceVersionFilter version,
                                         ResourcePublicationStatusFilter publicationStatus, List<String> sortList,
                                         boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList");
    sb.append(" AND (resource.<PROP.IS_USER_HOME> IS NULL OR resource.<PROP.IS_USER_HOME> <> true) ");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    if (version != null && version != ResourceVersionFilter.ALL) {
      sb.append(getVersionConditions(version, " AND ", "resource"));
    }
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      sb.append(getPublicationStatusConditions(" AND ", "resource"));
    }
    sb.append(" RETURN resource");
    sb.append(" ORDER BY resource.<PROP.NODE_SORT_ORDER>,").append(getOrderByExpression("resource", sortList));
    sb.append(" SKIP $offset");
    sb.append(" LIMIT $limit");
    return sb.toString();
  }

  public static String getAllCountQuery(ResourceVersionFilter version,
                                        ResourcePublicationStatusFilter publicationStatus,
                                        boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList");
    sb.append(" AND (resource.<PROP.IS_USER_HOME> IS NULL OR resource.<PROP.IS_USER_HOME> <> true) ");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    if (version != null && version != ResourceVersionFilter.ALL) {
      sb.append(getVersionConditions(version, " AND ", "resource"));
    }
    if (publicationStatus != null && publicationStatus != ResourcePublicationStatusFilter.ALL) {
      sb.append(getPublicationStatusConditions(" AND ", "resource"));
    }
    sb.append(" RETURN count(resource)");
    return sb.toString();
  }

  public static String getSearchIsBasedOnLookupQuery(List<String> sortList, boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList");
    sb.append(" AND (resource.<PROP.IS_BASED_ON> = {<PH.IS_BASED_ON>}) ");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    sb.append(" RETURN resource");
    sb.append(" ORDER BY resource.<PROP.NODE_SORT_ORDER>,").append(getOrderByExpression("resource", sortList));
    sb.append(" SKIP $offset");
    sb.append(" LIMIT $limit");
    return sb.toString();
  }

  public static String getSearchIsBasedOnCountQuery(boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.RESOURCE_TYPE> in $resourceTypeList");
    sb.append(" AND (resource.<PROP.IS_BASED_ON> = {<PH.IS_BASED_ON>}) ");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    sb.append(" RETURN count(resource)");
    return sb.toString();
  }

  public static String getResourceById() {
    return "" +
        " MATCH (resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN resource";
  }

  public static String getResourceTypeById() {
    return "" +
        " MATCH (resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN resource.<PROP.RESOURCE_TYPE>";
  }

  public static String setResourceOwner() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.RESOURCE_ID>}})" +
        " MERGE (user)-[:<REL.OWNS>]->(resource)" +
        " SET resource.<PROP.OWNED_BY> = {<PH.USER_ID>}" +
        " RETURN resource";
  }

  public static String removeResourceOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[relation:<REL.OWNS>]->(resource)" +
        " DELETE (relation)" +
        " SET resource.<PROP.OWNED_BY> = null" +
        " RETURN resource";
  }

  public static String resourceExists() {
    return "" +
        " MATCH (resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN COUNT(resource) = 1";
  }


  public static String getSpecialFoldersLookupQuery(List<String> sortList, boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    sb.append(" RETURN DISTINCT(resource)");
    sb.append(" ORDER BY resource.<PROP.NODE_SORT_ORDER>,");
    sb.append(getOrderByExpression("resource", sortList));
    sb.append(" SKIP $offset");
    sb.append(" LIMIT $limit");
    return sb.toString();
  }

  public static String getSpecialFoldersCountQuery(boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (resource)");
    sb.append(" WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" AND ", "resource"));
    }
    sb.append(
        " RETURN count(resource)"
    );
    return sb.toString();
  }

  public static String getTotalCount(CedarResourceType resourceType) {
    return "" +
        " MATCH (resource:" + NodeLabel.forCedarResourceType(resourceType) + ")" +
        " RETURN count(resource)";
  }
}
