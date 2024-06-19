package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.Map;

public class CypherQueryBuilderGroup extends AbstractCypherQueryBuilder {

  public static String createGroup() {
    StringBuilder sb = new StringBuilder();
    sb.append(" CREATE (group:<COMPOSEDLABEL.GROUP> {");

    sb.append(buildCreateAssignment(NodeProperty.ID)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.NAME)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.NAME_LOWER)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.DESCRIPTION)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.SPECIAL_GROUP)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.RESOURCE_TYPE));
    sb.append("})");

    sb.append(" RETURN group");
    return sb.toString();
  }

  public static String createGroupWithAdministrator() {
    StringBuilder sb = new StringBuilder();
    sb.append(" CREATE (group:<COMPOSEDLABEL.GROUP> {");

    sb.append(buildCreateAssignment(NodeProperty.ID)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.NAME)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.NAME_LOWER)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.DESCRIPTION)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.CREATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_BY)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.LAST_UPDATED_ON_TS)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.SPECIAL_GROUP)).append(",");
    sb.append(buildCreateAssignment(NodeProperty.RESOURCE_TYPE));
    sb.append("})");

    sb.append(" WITH group");

    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");

    sb.append(" MERGE (user)-[:<REL.ADMINISTERS>]->(group)");
    sb.append(" MERGE (user)-[:<REL.MEMBEROF>]->(group)");

    sb.append(" RETURN group");
    return sb.toString();
  }

  public static String findGroups() {
    return "" +
        " MATCH (group:<LABEL.GROUP>)" +
        " RETURN group" +
        " ORDER BY group.<PROP.NAME_LOWER>";
  }

  public static String updateGroupById(Map<NodeProperty, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("group", NodeProperty.LAST_UPDATED_BY));
    sb.append(buildSetter("group", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("group", NodeProperty.LAST_UPDATED_ON_TS));
    for (NodeProperty property : updateFields.keySet()) {
      sb.append(buildSetter("group", property));
    }
    sb.append(" RETURN group");
    return sb.toString();
  }

  public static String deleteGroupById() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})" +
        " DETACH DELETE group";
  }

  public static String getGroupUsersWithRelation(RelationLabel relationLabel) {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[:" + relationLabel + "]->(group)" +
        " RETURN user";
  }

  public static String getGroupBySpecialValue() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.SPECIAL_GROUP>:{<PH.SPECIAL_GROUP>}})" +
        " RETURN group";
  }

  public static String getGroupById() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN group";
  }

  public static String getGroupByName() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.NAME>:{<PH.NAME>}})" +
        " RETURN group";
  }

  public static String updateCategoryById(Map<NodeProperty, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("category", NodeProperty.LAST_UPDATED_BY));
    sb.append(buildSetter("category", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("category", NodeProperty.LAST_UPDATED_ON_TS));
    for (NodeProperty property : updateFields.keySet()) {
      sb.append(buildSetter("category", property));
    }
    sb.append(" RETURN category");
    return sb.toString();
  }

  public static String getGroupsByMemberUserId() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (user)-[:<REL.MEMBEROF>]->(group:<LABEL.GROUP>)" +
        " RETURN group";
  }

  public static String getGroupsByAdministratorUserId() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (user)-[:<REL.ADMINISTERS>]->(group:<LABEL.GROUP>)" +
        " RETURN group";
  }

  public static String getTotalCount() {
    return "" +
        " MATCH (group:<LABEL.GROUP>)" +
        " RETURN count(group)";
  }
}
