package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status rule this route shares with Dropwizard's admin servlet. The Monitor's health page
 * shows the proxied status as the other server's own verdict, so one failing check has to carry the
 * whole report to 500 rather than being averaged away.
 */
class CedarHealthCheckResourceTest {

  @Test
  void aReportIsHealthyOnlyWhenEveryCheckIs() {
    assertTrue(CedarHealthCheckResource.allHealthy(report()));
    assertTrue(CedarHealthCheckResource.allHealthy(report("neo4j", true)));
    assertTrue(CedarHealthCheckResource.allHealthy(report("neo4j", true, "mongo", true)));

    assertFalse(CedarHealthCheckResource.allHealthy(report("neo4j", false)));
    assertFalse(CedarHealthCheckResource.allHealthy(report("neo4j", true, "mongo", false)));
  }

  private static SortedMap<String, HealthCheck.Result> report(Object... namesAndOutcomes) {
    SortedMap<String, HealthCheck.Result> results = new TreeMap<>();
    for (int i = 0; i < namesAndOutcomes.length; i += 2) {
      boolean healthy = (Boolean) namesAndOutcomes[i + 1];
      results.put((String) namesAndOutcomes[i],
          healthy ? HealthCheck.Result.healthy() : HealthCheck.Result.unhealthy("offline"));
    }
    return results;
  }
}
