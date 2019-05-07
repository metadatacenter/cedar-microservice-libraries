package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public class CypherQueryBuilderUser extends AbstractCypherQueryBuilder {

  public static String createUser() {
    return "" +
        " CREATE (user:<COMPOSEDLABEL.USER> {" +
        buildCreateAssignment(NodeProperty.ID) + "," +
        buildCreateAssignment(NodeProperty.NAME) + "," +
        buildCreateAssignment(NodeProperty.FIRST_NAME) + "," +
        buildCreateAssignment(NodeProperty.LAST_NAME) + "," +
        buildCreateAssignment(NodeProperty.EMAIL) + "," +
        buildCreateAssignment(NodeProperty.CREATED_ON) + "," +
        buildCreateAssignment(NodeProperty.CREATED_ON_TS) + "," +
        buildCreateAssignment(NodeProperty.LAST_UPDATED_ON) + "," +
        buildCreateAssignment(NodeProperty.LAST_UPDATED_ON_TS) + "," +
        buildCreateAssignment(NodeProperty.API_KEYS) + "," +
        buildCreateAssignment(NodeProperty.API_KEY_MAP) + "," +
        buildCreateAssignment(NodeProperty.ROLES) + "," +
        buildCreateAssignment(NodeProperty.PERMISSIONS) + "," +
        buildCreateAssignment(NodeProperty.UI_PREFERENCES) + "," +
        buildCreateAssignment(NodeProperty.RESOURCE_TYPE) +
        " })" +
        " RETURN user";
  }

  public static String updateUser() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PROP.ID>}})");
    sb.append(buildSetter("user", NodeProperty.NAME));
    sb.append(buildSetter("user", NodeProperty.FIRST_NAME));
    sb.append(buildSetter("user", NodeProperty.LAST_NAME));
    sb.append(buildSetter("user", NodeProperty.EMAIL));
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON_TS));
    sb.append(buildSetter("user", NodeProperty.HOME_FOLDER_ID));
    sb.append(buildSetter("user", NodeProperty.API_KEYS));
    sb.append(buildSetter("user", NodeProperty.API_KEY_MAP));
    sb.append(buildSetter("user", NodeProperty.ROLES));
    sb.append(buildSetter("user", NodeProperty.PERMISSIONS));
    sb.append(buildSetter("user", NodeProperty.UI_PREFERENCES));
    sb.append(" RETURN user");
    return sb.toString();
  }

  public static String findUsers() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " RETURN user" +
        " ORDER BY LOWER(user.<PROP.NAME>)";
  }

  public static String getUserById() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PROP.ID>}})" +
        " RETURN user";
  }

  public static String addUserToGroup() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{userId}})" +
        " MATCH (group:<LABEL.GROUP> {<PROP.ID>:{groupId}})" +
        " MERGE (user)-[:<REL.MEMBEROF>]->(group)" +
        " RETURN user";
  }

  public static String getUserByApiKey() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " WHERE {apiKey} IN user.<PROP.API_KEYS>" +
        " RETURN user";
  }

}
