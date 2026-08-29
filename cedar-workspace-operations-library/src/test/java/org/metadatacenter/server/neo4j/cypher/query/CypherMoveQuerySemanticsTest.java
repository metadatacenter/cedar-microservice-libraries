package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherMoveQuerySemanticsTest {

  @Test
  void folderMoveLocksTheTreeAndChangesBothEdgesInOneStatement() {
    String query = CypherQueryBuilderFolder.moveFolder();

    assertTrue(query.contains("SET treeRoot.<PROP.ID> = treeRoot.<PROP.ID>"), query);
    assertTrue(query.contains("MATCH (newParent:<LABEL.FOLDER>"), query);
    assertTrue(query.contains("MATCH (folder)-[:<REL.CONTAINS>*0..]->(newParent)"), query);
    assertTrue(query.contains("DELETE oldRelation"), query);
    assertTrue(query.contains("MERGE (newParent)-[:<REL.CONTAINS>]->(folder)"), query);
  }

  @Test
  void artifactMoveLocksTheArtifactAndChangesBothEdgesInOneStatement() {
    String query = CypherQueryBuilderArtifact.moveArtifact();

    assertTrue(query.contains("SET artifact.<PROP.ID> = artifact.<PROP.ID>"), query);
    assertTrue(query.contains("MATCH (newParent:<LABEL.FOLDER>"), query);
    assertTrue(query.contains("DELETE oldRelation"), query);
    assertTrue(query.contains("MERGE (newParent)-[:<REL.CONTAINS>]->(artifact)"), query);
  }

  @Test
  void folderDeleteLocksBeforeCheckingEmptyAndNeverRecurses() {
    String query = CypherQueryBuilderFolder.deleteEmptyFolderById();

    assertTrue(query.contains("SET folder.<PROP.ID> = folder.<PROP.ID>"), query);
    assertTrue(query.contains("WHERE NOT EXISTS"), query);
    assertTrue(query.contains("MATCH (folder)-[:<REL.CONTAINS>]->()"), query);
    assertTrue(query.contains("DETACH DELETE folder"), query);
    assertTrue(query.contains("RETURN parent"), query);
    assertFalse(query.contains("<REL.CONTAINS>*"), query);
  }
}
