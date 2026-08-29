package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CypherGraphResourceRevisionSemanticsTest {

  @Test
  void plainEditsAdvanceTheirResourceRevision() {
    assertTrue(CypherQueryBuilderFolder.updateFolderById(Map.of(NodeProperty.NAME, "name"))
        .contains("folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1"));
    assertTrue(CypherQueryBuilderGroup.updateGroupById(Map.of(NodeProperty.NAME, "name"))
        .contains("group._cedarRevision = coalesce(group._cedarRevision, 1) + 1"));
    assertTrue(CypherQueryBuilderGroup.updateCategoryById(Map.of(NodeProperty.NAME, "name"))
        .contains("category._cedarRevision = coalesce(category._cedarRevision, 1) + 1"));
  }

  @Test
  void deletionLocksAndReadsTheCurrentResourceRevision() {
    assertTrue(CypherQueryBuilderFolder.lockFolderRevision().contains("SET folder._cedarRevision"));
    assertTrue(CypherQueryBuilderCategory.lockCategoryRevision().contains("SET category._cedarRevision"));
    assertTrue(CypherQueryBuilderGroup.lockGroupRevision().contains("SET group._cedarRevision"));
  }

  @Test
  void movingAFolderChangesItsRepresentationRevision() {
    assertTrue(CypherQueryBuilderFolder.moveFolder()
        .contains("folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1"));
  }

  @Test
  void categoryDeletionChecksContentAndNeverUsesDetachDelete() {
    String blockers = CypherQueryBuilderCategory.getCategoryDeletionBlockers();
    assertTrue(blockers.contains("CONTAINSCATEGORY"));
    assertTrue(blockers.contains("CONTAINSARTIFACT"));
    assertFalse(CypherQueryBuilderCategory.deleteCategoryById().contains("DETACH DELETE"));
  }
}
