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
    sb.append(buildCreateAssignment(NodeProperty.RESOURCE_TYPE)).append(",");
    sb.append("_cedarMembershipRevision:1");
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
    sb.append(buildCreateAssignment(NodeProperty.RESOURCE_TYPE)).append(",");
    sb.append("_cedarMembershipRevision:1");
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

  /** Reads the complete membership and its validator from one Neo4j transaction. */
  public static String getVersionedGroupUsers() {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})
        OPTIONAL MATCH (user:<LABEL.USER>)-[relation:<REL.MEMBEROF>|<REL.ADMINISTERS>]->(group)
        WITH group, user, collect(type(relation)) AS relationTypes
        RETURN coalesce(group._cedarMembershipRevision, 1) AS revision,
               user AS user,
               '<REL.ADMINISTERS>' IN relationTypes AS administrator,
               '<REL.MEMBEROF>' IN relationTypes AS member
        ORDER BY user.<PROP.ID>
        """;
  }

  /** Acquires the aggregate lock and returns the revision that must still match If-Match. */
  public static String lockGroupMembership() {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})
        SET group.<PROP.ID> = group.<PROP.ID>
        RETURN coalesce(group._cedarMembershipRevision, 1) AS revision
        """;
  }

  /**
   * Replaces both membership relation families after the caller has locked the group. All named
   * users are matched before any old relation is deleted, so a disappearing user changes nothing.
   * The result rows are the exact post-image paired with the incremented aggregate revision.
   */
  public static String replaceGroupUsers() {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.ID>}})
        MATCH (requestedUser:<LABEL.USER>)
        WHERE requestedUser.<PROP.ID> IN {<PH.USER_ID_LIST>}
        WITH group, collect(requestedUser) AS requestedUsers
        WHERE size(requestedUsers) = size({<PH.USER_ID_LIST>})
        OPTIONAL MATCH (oldUser:<LABEL.USER>)-[oldRelation:<REL.MEMBEROF>|<REL.ADMINISTERS>]->(group)
        DELETE oldRelation
        WITH DISTINCT group, requestedUsers
        FOREACH (user IN [candidate IN requestedUsers
                          WHERE candidate.<PROP.ID> IN {<PH.ADMINISTRATOR_ID_LIST>}] |
          MERGE (user)-[:<REL.ADMINISTERS>]->(group))
        FOREACH (user IN [candidate IN requestedUsers
                          WHERE candidate.<PROP.ID> IN {<PH.MEMBER_ID_LIST>}] |
          MERGE (user)-[:<REL.MEMBEROF>]->(group))
        SET group._cedarMembershipRevision = {<PH.CURRENT_REVISION>} + 1
        WITH group
        OPTIONAL MATCH (user:<LABEL.USER>)-[relation:<REL.MEMBEROF>|<REL.ADMINISTERS>]->(group)
        WITH group, user, collect(type(relation)) AS relationTypes
        RETURN group._cedarMembershipRevision AS revision,
               user AS user,
               '<REL.ADMINISTERS>' IN relationTypes AS administrator,
               '<REL.MEMBEROF>' IN relationTypes AS member
        ORDER BY user.<PROP.ID>
        """;
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

  /**
   * Matches on NAME_LOWER, not NAME: group names are unique without regard to case. Every group node
   * carries NAME_LOWER and group listings order by it, so matching the cased NAME here let "Curators"
   * and "curators" both pass the uniqueness check and coexist.
   */
  public static String getGroupByName() {
    return "" +
        " MATCH (group:<LABEL.GROUP> {<PROP.NAME_LOWER>:{<PH.NAME>}})" +
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
