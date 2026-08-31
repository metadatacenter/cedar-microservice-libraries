package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CedarDependencyHealthCheckTest {

  @Test
  void aReachableDependencyIsHealthyEitherWay() {
    assertTrue(CedarDependencyHealthCheck.gating("Neo4j", () -> {}).check().isHealthy());
    assertTrue(CedarDependencyHealthCheck.reporting("Redis", () -> {}).check().isHealthy());
  }

  @Test
  void onlyAGatingDependencyFailsTheServer() {
    HealthCheck.Result gating = CedarDependencyHealthCheck.gating("Neo4j", () -> {
      throw new IllegalStateException("offline");
    }).check();
    HealthCheck.Result reporting = CedarDependencyHealthCheck.reporting("Redis", () -> {
      throw new IllegalStateException("offline");
    }).check();

    assertFalse(gating.isHealthy());
    assertTrue(reporting.isHealthy());
  }

  @Test
  void bothOutcomesNameTheDependencyAndTheCause() {
    HealthCheck.Result gating = CedarDependencyHealthCheck.gating("Neo4j", () -> {
      throw new IllegalStateException("offline");
    }).check();
    HealthCheck.Result reporting = CedarDependencyHealthCheck.reporting("Redis", () -> {
      throw new IllegalStateException("offline");
    }).check();

    assertTrue(gating.getMessage().contains("Neo4j"));
    assertTrue(gating.getMessage().contains("offline"));
    assertTrue(reporting.getMessage().contains("Redis"));
    assertTrue(reporting.getMessage().contains("offline"));
  }

  /**
   * The reason each probe carries a timeout: the Neo4j driver waits 30 seconds to establish a
   * connection by default, and the container health check gives the whole endpoint 10. Without a
   * bound, a hung dependency would not be reported slow; it would hang {@code /healthcheck} itself.
   */
  @Test
  void aHangingProbeIsReportedRatherThanWaitedOut() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CedarDependencyHealthCheck check =
        CedarDependencyHealthCheck.gating("Neo4j", () -> release.await(1, TimeUnit.MINUTES), 100);

    long startedAt = System.nanoTime();
    HealthCheck.Result result = check.check();
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    release.countDown();
    assertFalse(result.isHealthy());
    assertTrue(result.getMessage().contains("did not answer"));
    assertTrue(elapsedMillis < 30_000, "the check waited " + elapsedMillis + "ms on a hung probe");
  }

  /** A second call joins the probe already running rather than queueing another behind it. */
  @Test
  void aHangingProbeCostsOneThreadRatherThanOnePerPoll() throws Exception {
    CountDownLatch release = new CountDownLatch(1);
    CountDownLatch started = new CountDownLatch(1);
    CedarDependencyHealthCheck check = CedarDependencyHealthCheck.gating("Neo4j", () -> {
      started.countDown();
      release.await(1, TimeUnit.MINUTES);
    }, 100);

    assertFalse(check.check().isHealthy());
    assertTrue(started.await(5, TimeUnit.SECONDS));
    assertFalse(check.check().isHealthy());

    release.countDown();
  }
}
