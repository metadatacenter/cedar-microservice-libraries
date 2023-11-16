package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;

public class CypherQueryBuilderGraph extends AbstractCypherQueryBuilder {


  public static String getOutgoingArcs() {
    return """
        MATCH
        (s {<PROP.ID>:{<PH.ID>}})-[r]->(t) RETURN s.<PROP.ID> AS sid, TYPE(r) AS type, t.<PROP.ID> AS tid ORDER BY s.<PROP.ID>, t.<PROP.ID>, type(r)
        """;
  }

  public static String getIncomingArcs() {
    return """
        MATCH
        (s)-[r]->(t {<PROP.ID>:{<PH.ID>}}) RETURN s.<PROP.ID> AS sid, TYPE(r) AS type, t.<PROP.ID> AS tid ORDER BY s.<PROP.ID>, t.<PROP.ID>, type(r)
        """;
  }

  public static String createArc(RelationLabel relationLabel) {
    return """
        MATCH (source {<PROP.ID>:$sourceId})
        MATCH (target {<PROP.ID>:$targetId})
        MERGE (source)-[:%s]->(target)
        RETURN source
        """.formatted(relationLabel.getValue());
  }

  public static String updateInclusionArcsDelete(RelationLabel relationLabel) {
    return """
        WITH {<PH.SOURCE_ID>} AS sourceId, {<PH.TARGET_IDS>} AS newTargets

        MATCH (s)-[r:%s]->(t)
        WHERE s.<PROP.ID> = sourceId AND NOT t.<PROP.ID> IN newTargets
        DELETE r
        """.formatted(relationLabel.getValue());
  }

  public static String updateInclusionArcsCreate(RelationLabel relationLabel) {
    return """
        WITH {<PH.SOURCE_ID>} AS sourceId, {<PH.TARGET_IDS>} AS newTargets

        WITH sourceId, newTargets
        MATCH (s {<PROP.ID>: sourceId}), (t)
        WHERE t.<PROP.ID> IN newTargets AND NOT (s)-[:%s]->(t)
        CREATE (s)-[:%s]->(t)
        """.formatted(relationLabel.getValue(), relationLabel.getValue());
  }

}
