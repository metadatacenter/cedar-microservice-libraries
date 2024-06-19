package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public class CypherQueryBuilderCategory extends AbstractCypherQueryBuilder {

  public static String getRootCategory() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY>) WHERE category.<PROP.PARENT_CATEGORY_ID> IS NULL" +
        " RETURN category";
  }

  public static String createCategory(CedarCategoryId parentCategoryId, CedarUserId userId) {
    StringBuilder sb = new StringBuilder();
    sb.append(" CREATE (category:<COMPOSEDLABEL.CATEGORY> {");
    // BaseDataGroup
    sb.append(buildCreateAssignment(NodeProperty.ID)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.RESOURCE_TYPE)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON)).append(",");
    // TimestampDataGroup
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON_TS)).append(",");
    // NameDescriptionIdentifierGroup
    sb.append(buildCreateAssignment(NodeProperty.NAME)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.NAME_LOWER)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.DESCRIPTION)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.IDENTIFIER)).append(",");
    // UsersDataGroup
    sb.append(buildCreateAssignment(NodeProperty.CREATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.OWNED_BY)).append(",");
    //
    sb.append(buildCreateAssignment(NodeProperty.PARENT_CATEGORY_ID));
    sb.append("})");

    if (parentCategoryId != null) {
      sb.append(" WITH category");
      sb.append(" MATCH (parent:<LABEL.CATEGORY> {<PROP.ID>:{<PH.PARENT_CATEGORY_ID>}})");
      sb.append(" MERGE (parent)-[:<REL.CONTAINSCATEGORY>]->(category)");
    }

    if (userId != null) {
      sb.append(" WITH category");
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
      sb.append(" MERGE (user)-[:<REL.OWNSCATEGORY>]->(category)");
    }

    sb.append(" RETURN category");
    return sb.toString();
  }

  public static String getCategoryById() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN category";
  }

  public static String getCategoryByIdentifier() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.IDENTIFIER>:{<PH.IDENTIFIER>}})" +
        " RETURN category";
  }

  public static String getCategoryByParentAndName() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.NAME>:{<PH.NAME>}, <PROP.PARENT_CATEGORY_ID>:{<PH.PARENT_CATEGORY_ID>}})" +
        " RETURN category";
  }

  public static String getAllCategories() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY>)" +
        " RETURN category" +
        " ORDER BY category.<PROP.NAME_LOWER>" +
        " SKIP $offset" +
        " LIMIT $limit";
  }

  public static String getTotalCount() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY>)" +
        " RETURN count(category)";
  }

  public static String deleteCategoryById() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " DETACH DELETE category";
  }

  public static String getCategoryOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (user)-[:<REL.OWNSCATEGORY>]->(category)" +
        " RETURN user";
  }

  public static String attachCategoryToArtifact() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ARTIFACT_ID>} })" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>} })" +
        " MERGE (category)-[:<REL.CONTAINSARTIFACT>]->(artifact)" +
        " RETURN category";
  }

  public static String detachCategoryFromArtifact() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ARTIFACT_ID>} })" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>} })" +
        " MATCH (category)-[relation:<REL.CONTAINSARTIFACT>]->(artifact)" +
        " DELETE (relation)" +
        " RETURN category";
  }

  public static String getCategoryPathsByArtifactId() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (category:<LABEL.CATEGORY>)-[:<REL.CONTAINSCATEGORY>*0..]->(directcategory:<LABEL.CATEGORY>)-[:<REL.CONTAINSARTIFACT>]->(artifact)" +
        " RETURN category";
  }

  public static String getCategoryPathIdsByArtifactId() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (category:<LABEL.CATEGORY>)-[:<REL.CONTAINSCATEGORY>*0..]->(directcategory:<LABEL.CATEGORY>)-[:<REL.CONTAINSARTIFACT>]->(artifact)" +
        " RETURN category.<PROP.ID>";
  }

  public static String getCategoryIdsByArtifactId() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (category:<LABEL.CATEGORY>)-[:<REL.CONTAINSARTIFACT>]->(artifact)" +
        " RETURN category.<PROP.ID>";
  }

  public static String setCategoryOwner() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (user)-[:<REL.OWNSCATEGORY>]->(category)" +
        " SET category.<PROP.OWNED_BY> = {<PH.USER_ID>}" +
        " RETURN category";
  }

  public static String removeCategoryOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[relation:<REL.OWNSCATEGORY>]->(category)" +
        " DELETE (relation)" +
        " SET category.<PROP.OWNED_BY> = null" +
        " RETURN category";
  }

  public static String categoryExists() {
    return "" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN COUNT(category) = 1";
  }

  public static String getCategoryPath() {
    return "" +
        " MATCH (directcategory:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (category:<LABEL.CATEGORY>)-[:<REL.CONTAINSCATEGORY>*0..]->(directcategory:<LABEL.CATEGORY>)" +
        " RETURN category";
  }
}
