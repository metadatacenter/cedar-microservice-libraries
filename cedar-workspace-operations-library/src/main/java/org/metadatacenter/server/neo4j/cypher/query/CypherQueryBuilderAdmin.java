package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

public class CypherQueryBuilderAdmin extends AbstractCypherQueryBuilder {

  public static String wipeAllData() {
    return " MATCH (n:" + NodeLabel.SimpleLabel.RESOURCE + ") DETACH DELETE n";
  }

  public static String wipeAllCategories() {
    return " MATCH (c:" + NodeLabel.ComposedLabel.CATEGORY + ") DETACH DELETE c";
  }

  public static String createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property) {
    return "CALL apoc.schema.assert({}, {" +
        nodeLabel.getSimpleLabel() +
        ":[\"" + Neo4JUtil.escapePropertyName(property.getValue()) +"\"]" +
        "});";
  }

  public static String createIndex(NodeLabel nodeLabel, NodeProperty property) {
    return "CALL apoc.schema.assert({" +
        nodeLabel.getSimpleLabel() +
        ":[\"" + Neo4JUtil.escapePropertyName(property.getValue()) +"\"]" +
        "}, {});";
  }

  public static String removeAllConstraintsAndIndices() {
    return " CALL apoc.schema.assert({}, {});";
  }
}
