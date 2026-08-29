package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherSiblingNameConstraintSemanticsTest {

  @Test
  void compositeConstraintIncludesParentAndLowercaseName() {
    String query = CypherQueryBuilderAdmin.createUniqueConstraint(NodeLabel.FOLDER,
        List.of(NodeProperty.PARENT_FOLDER_ID, NodeProperty.NAME_LOWER));

    assertTrue(query.contains("REQUIRE (n.parentFolderId, n.schema_name_lower) IS UNIQUE"), query);
  }

  @Test
  void foldersPersistAndMaintainTheParentKeyUsedByTheConstraint() {
    FolderServerFolder folder = new FolderServerFolder();
    String create = CypherQueryBuilderFolder.createFolderAsChildOfId(folder);
    String move = CypherQueryBuilderFolder.moveFolder();

    assertTrue(create.contains("parentFolderId: $parentFolderId"), create);
    assertTrue(move.contains("SET folder.<PROP.PARENT_FOLDER_ID> = {<PH.PARENT_FOLDER_ID>}"), move);
  }

  @Test
  void siblingPreflightQueriesUseTheLowercaseNameProperty() {
    assertTrue(CypherQueryBuilderCategory.getCategoryByParentAndName()
        .contains("<PROP.NAME_LOWER>:{<PH.NAME>}"));
    assertTrue(CypherQueryBuilderFilesystemResource.getResourceByParentIdAndName()
        .contains("child.<PROP.NAME_LOWER> = {<PH.NAME>}"));
  }
}
