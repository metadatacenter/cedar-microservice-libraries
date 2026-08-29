package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermission;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroup;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionGroupPermissionPair;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUser;
import org.metadatacenter.server.security.model.permission.category.CategoryPermissionUserPermissionPair;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
  void categoryOwnerAndAclChangesUseOneWriteTransaction() {
    Neo4JProxyCategoryPermission proxy = mock(Neo4JProxyCategoryPermission.class, CALLS_REAL_METHODS);
    doReturn(true).when(proxy).executeWriteBatch(anyList(), eq("updating category permissions"));

    var categoryId = CedarCategoryId.build("https://repo.example/categories/c1");
    var newOwnerId = new CategoryPermissionUser("https://repo.example/users/owner").getResourceId();
    var removeUser = new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser("https://repo.example/users/remove"), CategoryPermission.ATTACH);
    var addUser = new CategoryPermissionUserPermissionPair(
        new CategoryPermissionUser("https://repo.example/users/add"), CategoryPermission.WRITE);
    var removeGroup = new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup("https://repo.example/groups/remove"), CategoryPermission.ATTACH);
    var addGroup = new CategoryPermissionGroupPermissionPair(
        new CategoryPermissionGroup("https://repo.example/groups/add"), CategoryPermission.WRITE);

    assertTrue(proxy.updatePermissionsAtomically(categoryId, newOwnerId,
        Set.of(removeUser), Set.of(addUser), Set.of(removeGroup), Set.of(addGroup)));

    @SuppressWarnings({"unchecked", "rawtypes"})
    ArgumentCaptor<List<CypherQuery>> batch = ArgumentCaptor.forClass((Class) List.class);
    verify(proxy).executeWriteBatch(batch.capture(), eq("updating category permissions"));
    verify(proxy, never()).executeWrite(any(CypherQuery.class), anyString());
    assertEquals(6, batch.getValue().size());
  }
}
