package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;

public class CypherQueryBuilderGraph extends AbstractCypherQueryBuilder {


  public static String getOutgoingArcs() {
    return """
        MATCH
        (s:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})-[r]->(t:<LABEL.RESOURCE>) RETURN s.<PROP.ID> AS sid, TYPE(r) AS type, t.<PROP.ID> AS tid ORDER BY s.<PROP.ID>, t.<PROP.ID>, type(r)
        """;
  }

  public static String getIncomingArcs() {
    return """
        MATCH
        (s:<LABEL.RESOURCE>)-[r]->(t:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}}) RETURN s.<PROP.ID> AS sid, TYPE(r) AS type, t.<PROP.ID> AS tid ORDER BY s.<PROP.ID>, t.<PROP.ID>, type(r)
        """;
  }

  public static String createArc(RelationLabel relationLabel) {
    return """
        MATCH (source:<LABEL.RESOURCE> {<PROP.ID>:$sourceId})
        MATCH (target:<LABEL.RESOURCE> {<PROP.ID>:$targetId})
        MERGE (source)-[:%s]->(target)
        RETURN source
        """.formatted(relationLabel.getValue());
  }

  public static String updateInclusionArcsDelete(RelationLabel relationLabel) {
    return """
        WITH {<PH.SOURCE_ID>} AS sourceId, {<PH.TARGET_IDS>} AS newTargets

        MATCH (s:<LABEL.FILESYSTEM_RESOURCE>)-[r:%s]->(t:<LABEL.FILESYSTEM_RESOURCE>)
        WHERE s.<PROP.ID> = sourceId AND NOT t.<PROP.ID> IN newTargets
        DELETE r
        """.formatted(relationLabel.getValue());
  }

  public static String updateInclusionArcsCreate(RelationLabel relationLabel) {
    return """
        WITH {<PH.SOURCE_ID>} AS sourceId, {<PH.TARGET_IDS>} AS newTargets

        WITH sourceId, newTargets
        MATCH (s:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>: sourceId}), (t:<LABEL.FILESYSTEM_RESOURCE>)
        WHERE t.<PROP.ID> IN newTargets AND NOT (s)-[:%s]->(t)
        CREATE (s)-[:%s]->(t)
        """.formatted(relationLabel.getValue(), relationLabel.getValue());
  }

  public static String getIncludingTemplates(boolean addPermissionConditions) {
    return getIncludingResources(addPermissionConditions);
  }

  public static String getIncludingElements(boolean addPermissionConditions) {
    return getIncludingResources(addPermissionConditions);
  }

  /**
   * The artifacts of one type that include the given artifact, filtered to the ones the user may read.
   *
   * <p>Inclusion is a property of the artifacts, not of who may see them, so the arc alone matches every
   * including artifact in the installation. Without the permission conditions this returns other people's
   * artifacts: the callers use it to build the tree of artifacts affected by a change, and that tree is
   * both shown to the user and taken as the list of artifacts to rewrite.
   *
   * <p>The conditions are the same ones the rest of the graph queries apply — ownership or a read or write
   * grant, held directly or through a group, on the artifact or on a folder containing it — so an artifact
   * appears here exactly when {@code userHasReadAccessToResource} would allow it.
   *
   * <p>{@code addPermissionConditions} is false for a caller holding {@code READ_NOT_READABLE_NODE}, which
   * is how every other conditioned listing treats that permission. The user is then not matched at all
   * rather than matched and ignored, so the query never depends on a node for a caller whose access does
   * not come from the graph.
   *
   * <p>Templates and elements ask the same question of different node types, which the resource type
   * parameter carries. One query answers both; two identical copies would only offer somewhere for the
   * conditions to be added to one and not the other.
   */
  private static String getIncludingResources(boolean addPermissionConditions) {
    StringBuilder sb = new StringBuilder();
    if (addPermissionConditions) {
      sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    }
    sb.append(" MATCH (source:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>} })");
    sb.append(" MATCH (including:<LABEL.FILESYSTEM_RESOURCE> {<PROP.RESOURCE_TYPE>:{<PH.RESOURCE_TYPE>}})");
    sb.append(" MATCH (including)-[:<REL.INCLUDES>]->(source)");
    if (addPermissionConditions) {
      sb.append(getResourcePermissionConditions(" WHERE ", "including"));
    }
    sb.append(" RETURN including");
    return sb.toString();
  }
}
