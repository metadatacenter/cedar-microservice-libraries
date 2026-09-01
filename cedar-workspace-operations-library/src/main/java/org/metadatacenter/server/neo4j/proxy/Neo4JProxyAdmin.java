package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderAdmin;

import java.util.List;

public class Neo4JProxyAdmin extends AbstractNeo4JProxy {

  Neo4JProxyAdmin(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  boolean wipeAllData() {
    String cypher = CypherQueryBuilderAdmin.wipeAllData();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "deleting all data");
  }

  boolean wipeAllCategories() {
    String cypher = CypherQueryBuilderAdmin.wipeAllCategories();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "deleting all categories");
  }

  boolean createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property) {
    String cypher = CypherQueryBuilderAdmin.createUniqueConstraint(nodeLabel, property);
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "creating unique constraint");
  }

  boolean createUniqueConstraint(NodeLabel nodeLabel, List<NodeProperty> properties) {
    String cypher = CypherQueryBuilderAdmin.createUniqueConstraint(nodeLabel, properties);
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "creating composite unique constraint");
  }

  boolean backfillFolderParentIds() {
    CypherQuery q = new CypherQueryLiteral(CypherQueryBuilderAdmin.backfillFolderParentIds());
    return executeWrite(q, "backfilling folder parent identifiers");
  }

  boolean createIndex(NodeLabel nodeLabel, NodeProperty property) {
    String cypher = CypherQueryBuilderAdmin.createIndex(nodeLabel, property);
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "creating index");
  }

  boolean removeAllConstraintsAndIndices() {
    String cypher = CypherQueryBuilderAdmin.removeAllConstraintsAndIndices();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeWrite(q, "removing all constraints and indices");
  }
}
