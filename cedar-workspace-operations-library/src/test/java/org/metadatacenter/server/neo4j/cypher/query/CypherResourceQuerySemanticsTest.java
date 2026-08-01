package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;

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
}
