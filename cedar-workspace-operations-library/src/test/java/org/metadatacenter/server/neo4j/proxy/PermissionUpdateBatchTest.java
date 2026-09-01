package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategoryPermission;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionUpdateBatchTest {

  @Test
  void resourceAclReplacementIsOneVersionedCypherMutation() {
    String query = CypherQueryBuilderFilesystemResourcePermission.replacePermissions();

    assertTrue(query.contains("collect(DISTINCT oldOwner) + collect(DISTINCT oldGrant)"));
    assertTrue(query.contains("FOREACH (relation IN oldRelations | DELETE relation)"));
    assertTrue(query.contains("resource._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1"));
    assertTrue(query.contains("resource._cedarAclRevision AS revision"));
  }

  @Test
  void categoryAclReplacementIsOneVersionedCypherMutation() {
    String query = CypherQueryBuilderCategoryPermission.replacePermissions();

    assertTrue(query.contains("collect(DISTINCT oldOwner) + collect(DISTINCT oldGrant)"));
    assertTrue(query.contains("FOREACH (relation IN oldRelations | DELETE relation)"));
    assertTrue(query.contains("category._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1"));
    assertTrue(query.contains("category._cedarAclRevision AS revision"));
  }
}
