package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;

public class CypherQueryBuilderCategoryPermission extends AbstractCypherQueryBuilder {

  public static String getVersionedPermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        OPTIONAL MATCH (owner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (principal)-[grant:CANATTACHCATEGORY|CANWRITECATEGORY]->(category)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          coalesce(category._cedarAclRevision, 1) AS revision
        """;
  }

  public static String lockPermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1)
        RETURN category._cedarAclRevision AS revision
        """;
  }

  public static String replacePermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        MATCH (owner:<LABEL.USER> {<PROP.ID>:{<PH.OWNER_ID>}})
        OPTIONAL MATCH (user:<LABEL.USER>)
        WHERE user.<PROP.ID> IN {<PH.USER_ID_LIST>}
        WITH category, owner, collect(DISTINCT user) AS users
        WHERE size(users) = size({<PH.USER_ID_LIST>})
        OPTIONAL MATCH (group:<LABEL.GROUP>)
        WHERE group.<PROP.ID> IN {<PH.GROUP_ID_LIST>}
        WITH category, owner, users, collect(DISTINCT group) AS groups
        WHERE size(groups) = size({<PH.GROUP_ID_LIST>})
        OPTIONAL MATCH ()-[oldOwner:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH ()-[oldGrant:CANATTACHCATEGORY|CANWRITECATEGORY]->(category)
        WITH category, owner, users, groups,
          collect(DISTINCT oldOwner) + collect(DISTINCT oldGrant) AS oldRelations
        FOREACH (relation IN oldRelations | DELETE relation)
        CREATE (owner)-[:<REL.OWNSCATEGORY>]->(category)
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.ATTACH_USER_ID_LIST>}] |
          CREATE (user)-[:CANATTACHCATEGORY]->(category))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.WRITE_USER_ID_LIST>}] |
          CREATE (user)-[:CANWRITECATEGORY]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.ATTACH_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANATTACHCATEGORY]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.WRITE_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANWRITECATEGORY]->(category))
        SET category._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH category
        OPTIONAL MATCH (newOwner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (principal)-[grant:CANATTACHCATEGORY|CANWRITECATEGORY]->(category)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN newOwner AS owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS permission,
          category._cedarAclRevision AS revision
        """;
  }

  public static String addPermissionToCategoryForUser(CategoryPermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (user)-[:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1" +
        " RETURN user";
  }

  public static String addPermissionToCategoryForGroup(CategoryPermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (group)-[:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1" +
        " RETURN group";
  }

  public static String removePermissionForCategoryFromUser(CategoryPermission permission) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MATCH (user)-[relation:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " DELETE (relation)" +
        " SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1" +
        " RETURN category";
  }

  public static String removePermissionForCategoryFromGroup(CategoryPermission permission) {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>} })" +
        " MATCH (group)-[relation:" + RelationLabel.forCategoryPermission(permission) + "]->(category)" +
        " DELETE (relation)" +
        " SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1" +
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
