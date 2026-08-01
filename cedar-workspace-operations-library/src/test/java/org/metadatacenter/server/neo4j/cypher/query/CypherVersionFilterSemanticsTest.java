package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CypherVersionFilterSemanticsTest {

  @Test
  void latestByStatusRequiresATrueFlagOrBothLegacyFlagsToBeAbsent() {
    String condition = VersionConditionHarness.build(ResourceVersionFilter.LATEST_BY_STATUS);

    assertEquals(" AND (resource.<PROP.IS_LATEST_DRAFT_VERSION> = true" +
        " OR resource.<PROP.IS_LATEST_PUBLISHED_VERSION> = true" +
        " OR (resource.<PROP.IS_LATEST_DRAFT_VERSION> IS NULL" +
        " AND resource.<PROP.IS_LATEST_PUBLISHED_VERSION> IS NULL))", condition);
  }

  private static final class VersionConditionHarness extends AbstractCypherQueryBuilder {
    private static String build(ResourceVersionFilter version) {
      return getVersionConditions(version, "AND", "resource");
    }
  }
}
