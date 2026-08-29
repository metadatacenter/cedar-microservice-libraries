package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CypherGraphResourceRevisionSemanticsTest {

  @Test
  void plainEditsAdvanceTheirResourceRevision() {
    String folderUpdate = CypherQueryBuilderFolder.updateFolderById(Map.of(NodeProperty.NAME, "name"));
    String groupUpdate = CypherQueryBuilderGroup.updateGroupById(Map.of(NodeProperty.NAME, "name"));
    String categoryUpdate = CypherQueryBuilderGroup.updateCategoryById(Map.of(NodeProperty.NAME, "name"));

    assertTrue(folderUpdate.contains("folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1"));
    assertTrue(folderUpdate.contains("RETURN folder AS resource, folder._cedarRevision AS revision"));
    assertTrue(groupUpdate.contains("group._cedarRevision = coalesce(group._cedarRevision, 1) + 1"));
    assertTrue(groupUpdate.contains("RETURN group AS resource, group._cedarRevision AS revision"));
    assertTrue(categoryUpdate.contains("category._cedarRevision = coalesce(category._cedarRevision, 1) + 1"));
    assertTrue(categoryUpdate.contains("RETURN category AS resource, category._cedarRevision AS revision"));
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
  void openStateChangesAdvanceAndReturnTheResourceRevision() {
    for (String query : new String[]{
        CypherQueryBuilderArtifact.setOpen(), CypherQueryBuilderArtifact.setNotOpen(),
        CypherQueryBuilderFolder.setOpen(), CypherQueryBuilderFolder.setNotOpen()}) {
      assertTrue(query.contains("_cedarRevision = coalesce("));
      assertTrue(query.contains("AS resource"));
      assertTrue(query.contains("AS revision"));
    }
    assertTrue(CypherQueryBuilderArtifact.lockArtifactRevision().contains("SET artifact._cedarRevision"));
    assertTrue(CypherQueryBuilderFolder.lockFolderRevision().contains("SET folder._cedarRevision"));
  }

  @Test
  void categoryDeletionChecksContentAndNeverUsesDetachDelete() {
    String blockers = CypherQueryBuilderCategory.getCategoryDeletionBlockers();
    assertTrue(blockers.contains("CONTAINSCATEGORY"));
    assertTrue(blockers.contains("CONTAINSARTIFACT"));
    assertFalse(CypherQueryBuilderCategory.deleteCategoryById().contains("DETACH DELETE"));
  }
}
