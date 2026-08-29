package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public class CypherQueryBuilderUser extends AbstractCypherQueryBuilder {

  public static String createUser() {
    return "" +
        " CREATE (user:<COMPOSEDLABEL.USER> {" +
        buildCreateAssignment(NodeProperty.ID) + "," +
        buildCreateAssignment(NodeProperty.NAME) + "," +
        buildCreateAssignment(NodeProperty.NAME_LOWER) + "," +
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
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("user", NodeProperty.NAME));
    sb.append(buildSetter("user", NodeProperty.NAME_LOWER));
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
    return """
        MATCH (user:<LABEL.USER>)
        RETURN user
        ORDER BY user.<PROP.NAME_LOWER>
        """;
  }

  /**
   * Stamps the update time and returns the user, opening a change to that user's API keys. The SET
   * is what takes the node's write lock; a bare MATCH would return the same row and serialize
   * nothing, leaving the keys free to move before the change is written.
   */
  public static String touchAndReadUser() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON_TS));
    sb.append(" RETURN user");
    return sb.toString();
  }

  /**
   * Takes the user node's write lock without changing its value, then returns the state protected by
   * that lock. Profile patches are computed in Java, so their read must be serialized with the write
   * or two patches can both be derived from the same preferences snapshot.
   */
  public static String lockAndReadUserProfile() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})
        SET user.<PROP.UI_PREFERENCES> = user.<PROP.UI_PREFERENCES>
        RETURN user
        """;
  }

  /** Writes profile preferences and timestamps, leaving credentials and authorization state alone. */
  public static String updateUserProfile() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("user", NodeProperty.LAST_UPDATED_ON_TS));
    sb.append(buildSetter("user", NodeProperty.UI_PREFERENCES));
    sb.append(" RETURN user");
    return sb.toString();
  }

  /**
   * Writes the API key properties and nothing else. {@link #updateUser()} sets every field of the
   * node from one in-memory snapshot, so using it to change a key also wrote back the name, email,
   * roles, permissions and UI preferences as they stood when that snapshot was read.
   */
  public static String updateUserApiKeys() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("user", NodeProperty.API_KEYS));
    sb.append(buildSetter("user", NodeProperty.API_KEY_MAP));
    sb.append(" RETURN user");
    return sb.toString();
  }

  public static String getUserById() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})
        RETURN user
        """;
  }

  public static String addUserToGroup() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MERGE (user)-[:<REL.MEMBEROF>]->(group)
        RETURN user
        """;
  }

  public static String getUserByApiKey() {
    return """
        MATCH (user:<LABEL.USER>)
        WHERE {<PH.API_KEY>} IN user.<PROP.API_KEYS>
        RETURN user
        """;
  }

  public static String userExists() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.ID>}})
        RETURN COUNT(user) = 1
        """;
  }

  public static String getTotalCount() {
    return """
        MATCH (user:<LABEL.USER>)
        RETURN count(user)
        """;
  }
}
