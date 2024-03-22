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

  public static String getIncludingTemplates() {
    return """
        MATCH (source:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>} })
        MATCH (including:<LABEL.FILESYSTEM_RESOURCE> {<PROP.RESOURCE_TYPE>:{<PH.RESOURCE_TYPE>}})
        MATCH (including)-[:<REL.INCLUDES>]->(source)
        RETURN including
        """;
  }

  public static String getIncludingElements() {
    return """
        MATCH (source:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>} })
        MATCH (including:<LABEL.FILESYSTEM_RESOURCE> {<PROP.RESOURCE_TYPE>:{<PH.RESOURCE_TYPE>}})
        MATCH (including)-[:<REL.INCLUDES>]->(source)
        RETURN including
        """;
  }
}
