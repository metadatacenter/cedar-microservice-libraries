package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;

public class CypherQueryBuilderCategoryPermission extends AbstractCypherQueryBuilder {

  public static String addPermissionToCategoryForUser(CategoryPermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (user)-[:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " RETURN user";
  }

  public static String addPermissionToCategoryForGroup(CategoryPermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (group)-[:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " RETURN group";
  }

  public static String removePermissionForCategoryFromUser(CategoryPermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MATCH (user)-[relation:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " DELETE (relation)" +
        " RETURN category";
  }

  public static String removePermissionForCategoryFromGroup(CategoryPermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>} })" +
        " MATCH (group)-[relation:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " DELETE (relation)" +
        " RETURN category";
  }

  public static String userCanWriteCategory() {
    return userHasPermissionOnCategory(RelationLabel.CANWRITECATEGORY);
  }

  public static String userCanAttachCategory() {
    return userHasPermissionOnCategory(RelationLabel.CANATTACHCATEGORY);
  }

  private static String userHasPermissionOnCategory(RelationLabel relationLabel) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    sb.append(" MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})");
    sb.append(" WHERE");

    sb.append(" (");
    sb.append(getUserToResourceRelationWithContains(RelationLabel.OWNSCATEGORY, "category"));
    if (relationLabel == RelationLabel.CANATTACHCATEGORY) {
      sb.append(" OR ");
      sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANATTACHCATEGORY, "category"));
    }
    sb.append(" OR ");
    sb.append(getUserToResourceRelationThroughGroupWithContains(RelationLabel.CANWRITECATEGORY, "category"));
    sb.append(" )");
    sb.append(" RETURN user");
    return sb.toString();
  }

  public static String getUsersWithDirectPermissionOnCategory(RelationLabel relationLabel) {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[:" + relationLabel + "]->(category)" +
        " RETURN user";
  }

  public static String getGroupsWithDirectPermissionOnCategory(RelationLabel relationLabel) {
    return "" +
        " MATCH (group:<LABEL.GROUP>)" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (group)-[:" + relationLabel + "]->(category)" +
        " RETURN group";
  }
}
