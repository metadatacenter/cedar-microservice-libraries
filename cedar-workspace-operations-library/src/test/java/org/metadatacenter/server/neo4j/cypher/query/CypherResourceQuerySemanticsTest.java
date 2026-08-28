package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherResourceQuerySemanticsTest {

  @Test
  void sharedWithMeLookupAndCountBothDeduplicateResourcesReachedThroughMultipleGroups() {
    String lookup = CypherQueryBuilderResource.getSharedWithMeLookupQuery(
        ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, List.of("name"));
    String count = CypherQueryBuilderResource.getSharedWithMeCountQuery(
        ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL);

    assertTrue(lookup.contains("RETURN DISTINCT(resource)"), lookup);
    assertTrue(count.contains("RETURN count(DISTINCT resource)"), count);
  }

  @Test
  void everyOffsetPaginatedResourceQueryEndsItsOrderingWithUniqueIdentity() {
    List<String> resourceQueries = List.of(
        CypherQueryBuilderResource.getSharedWithMeLookupQuery(
            ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, List.of("name")),
        CypherQueryBuilderResource.getAllLookupQuery(
            ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, List.of("name"), true),
        CypherQueryBuilderResource.getSearchIsBasedOnLookupQuery(List.of("name"), true),
        CypherQueryBuilderResource.getSpecialFoldersLookupQuery(List.of("name"), true),
        CypherQueryBuilderResource.getSpecialFoldersLookupQuery(List.of("name"), false),
        CypherQueryBuilderFilesystemResource.getAllResourcesLookupQuery(List.of("name")),
        CypherQueryBuilderFilesystemResource.getSharedWithEverybodyLookupQuery(
            ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, List.of("name")));

    for (String query : resourceQueries) {
      assertTrue(query.matches("(?s).*ORDER BY.*resource\\.<PROP\\.ID>\\s*SKIP \\$offset.*"), query);
    }

    String folderContents = CypherQueryBuilderFolderContent.getFolderContentsFilteredLookupQuery(
        List.of("name"), ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL);
    assertTrue(folderContents.matches("(?s).*ORDER BY.*child\\.<PROP\\.ID>\\s*SKIP \\$offset.*"), folderContents);
  }

  @Test
  void resourceQueriesRejectUnknownSortFieldsBeforeBuildingCypher() {
    assertThrows(IllegalArgumentException.class,
        () -> CypherQueryBuilderFilesystemResource.getAllResourcesLookupQuery(
            List.of("name DESC MATCH (injected) RETURN injected")));
  }
}
