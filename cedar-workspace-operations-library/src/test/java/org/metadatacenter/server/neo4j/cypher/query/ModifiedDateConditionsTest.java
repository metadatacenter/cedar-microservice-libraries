package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import static org.junit.jupiter.api.Assertions.*;

class ModifiedDateConditionsTest {
  @Test void convertsMillisecondBoundsToWholeGraphSecondsWithoutChangingInclusion() {
    for (long after : new long[]{-1001, -1000, -999, 0, 1, 999, 1000, 1001, 1790000000000L}) {
      var range = new ModifiedDateRange(after, after + 2001);
      var params = new CypherParameters();
      ModifiedDateConditions.parameters(params, range);
      long lower = (long) params.asMap().get("modifiedAfter");
      long upper = (long) params.asMap().get("modifiedBefore");
      for (long second = Math.floorDiv(after, 1000) - 1; second <= Math.floorDiv(after, 1000) + 4; second++) {
        assertEquals(range.contains(second * 1000), second >= lower && second < upper, "second=" + second);
      }
    }
  }
}
