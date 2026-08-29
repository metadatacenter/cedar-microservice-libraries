package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

import java.util.List;
import java.util.stream.Collectors;

public class CypherQueryBuilderAdmin extends AbstractCypherQueryBuilder {

  public static String wipeAllData() {
    return " MATCH (n:" + NodeLabel.SimpleLabel.RESOURCE + ") DETACH DELETE n";
  }

  public static String wipeAllCategories() {
    return " MATCH (c:" + NodeLabel.ComposedLabel.CATEGORY + ") DETACH DELETE c";
  }

  public static String createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property) {
    return createUniqueConstraint(nodeLabel, List.of(property));
  }

  public static String createUniqueConstraint(NodeLabel nodeLabel, List<NodeProperty> properties) {
    if (properties == null || properties.isEmpty()) {
      throw new IllegalArgumentException("A uniqueness constraint needs at least one property");
    }
    String constrainedExpression = properties.size() == 1
        ? "n." + Neo4JUtil.escapePropertyName(properties.get(0).getValue())
        : properties.stream()
            .map(property -> "n." + Neo4JUtil.escapePropertyName(property.getValue()))
            .collect(Collectors.joining(", ", "(", ")"));
    return "CREATE CONSTRAINT IF NOT EXISTS " +
        "FOR (n:" + nodeLabel.getSimpleLabel() + ") " +
        "REQUIRE " + constrainedExpression + " IS UNIQUE;";
  }

  /** Populate the denormalized parent key used by the folder sibling-name constraint. */
  public static String backfillFolderParentIds() {
    return " MATCH (parent:" + NodeLabel.SimpleLabel.FOLDER + ")"
        + "-[:<REL.CONTAINS>]->(child:" + NodeLabel.SimpleLabel.FOLDER + ")"
        + " SET child.<PROP.PARENT_FOLDER_ID> = parent.<PROP.ID>";
  }

  public static String createIndex(NodeLabel nodeLabel, NodeProperty property) {
    return "CREATE INDEX " +
        nodeLabel.getSimpleLabel() + "_" + Neo4JUtil.escapePropertyName(property.getValue()) + " " +
        "FOR (n:" + nodeLabel.getSimpleLabel() + ") " +
        "ON (n." + Neo4JUtil.escapePropertyName(property.getValue()) + ");";
  }

  public static String removeAllConstraintsAndIndices() {
    return " CALL apoc.schema.assert({}, {});";
  }
}
