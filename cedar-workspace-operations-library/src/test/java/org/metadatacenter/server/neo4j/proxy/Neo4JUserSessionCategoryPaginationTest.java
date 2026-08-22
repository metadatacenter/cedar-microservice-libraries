package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4JUserSessionCategoryPaginationTest {

  @Test
  void loadsEveryCategoryAcrossRepositoryPagesWithoutReordering() {
    List<FolderServerCategory> stored = IntStream.range(0, 2501)
        .mapToObj(Neo4JUserSessionCategoryPaginationTest::category)
        .toList();
    List<Integer> requestedOffsets = new ArrayList<>();

    List<FolderServerCategory> loaded = Neo4JUserSessionCategoryService.loadAllCategories((limit, offset) -> {
      requestedOffsets.add(offset);
      return stored.subList(offset, Math.min(offset + limit, stored.size()));
    });

    assertEquals(2501, loaded.size());
    assertEquals(List.of("category-0", "category-1000", "category-2000", "category-2500"),
        List.of(loaded.get(0).getId(), loaded.get(1000).getId(), loaded.get(2000).getId(), loaded.get(2500).getId()));
    assertEquals(List.of(0, 1000, 2000), requestedOffsets);
  }

  @Test
  void repositoryOrderHasIdentityTieBreakerForStableOffsetPagination() {
    String query = CypherQueryBuilderCategory.getAllCategories();

    assertTrue(query.contains("ORDER BY category.<PROP.NAME_LOWER>, category.<PROP.ID>"));
  }

  @Test
  void singleCategoryPathIsOrderedFromLeafOutwardForPrependingIntoRootFirstOrder() {
    String query = CypherQueryBuilderCategory.getCategoryPath();

    assertTrue(query.contains("MATCH path="));
    assertTrue(query.contains("ORDER BY length(path)"));
  }

  @Test
  void attachedCategoryPathsStayGroupedByLeafAndOrderedOutward() {
    String query = CypherQueryBuilderCategory.getCategoryPathsByArtifactId();

    assertTrue(query.contains("MATCH path="));
    assertTrue(query.contains("ORDER BY directcategory.<PROP.ID>, length(path)"));
  }

  private static FolderServerCategory category(int index) {
    FolderServerCategory category = new FolderServerCategory();
    category.setId("category-" + index);
    category.setName("Category " + (index % 10));
    return category;
  }
}
