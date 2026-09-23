package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

public final class ModifiedDateConditions {
  private ModifiedDateConditions() {}
  public static String and(String node, ModifiedDateRange range) {
    return (range.after() == null ? "" : " AND " + node + ".<PROP.LAST_UPDATED_ON_TS> >= $modifiedAfter")
        + (range.before() == null ? "" : " AND " + node + ".<PROP.LAST_UPDATED_ON_TS> < $modifiedBefore");
  }
  // Graph provenance uses whole epoch seconds. Round both API boundaries upward:
  // a second is included exactly when its millisecond instant is in [after, before).
  private static long epochSecondCeiling(long milliseconds) {
    return Math.floorDiv(milliseconds, 1000) + (Math.floorMod(milliseconds, 1000) == 0 ? 0 : 1);
  }
  public static void parameters(CypherParameters params, ModifiedDateRange range) {
    if (range.after() != null) params.put(ParameterPlaceholder.MODIFIED_AFTER, epochSecondCeiling(range.after()));
    if (range.before() != null) params.put(ParameterPlaceholder.MODIFIED_BEFORE, epochSecondCeiling(range.before()));
  }
}
